#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <termios.h>
#include <unistd.h>
#include <dirent.h>

/* JNI (класс Pty, @JvmStatic):
   Java_client_wexmanager_feature_terminal_Pty_nativeOpen
   Java_client_wexmanager_feature_terminal_Pty_nativeSetSize
   Java_client_wexmanager_feature_terminal_Pty_nativeKillGroup
   Java_client_wexmanager_feature_terminal_Pty_nativeClose */

extern char **environ;

static void throw_io(JNIEnv *env, const char *msg) {
    jclass cls = (*env)->FindClass(env, "java/io/IOException");
    if (cls != NULL) (*env)->ThrowNew(env, cls, msg);
}

static void throw_errno(JNIEnv *env, const char *what) {
    char err[160];
    char buf[256];
    err[0] = '\0';
    strerror_r(errno, err, sizeof(err));
    snprintf(buf, sizeof(buf), "%s: %s", what, err);
    throw_io(env, buf);
}

static int copy_jstring(JNIEnv *env, jstring value, char *buf, size_t cap) {
    const char *utf = (*env)->GetStringUTFChars(env, value, NULL);
    if (utf == NULL) return -1;
    size_t n = strlen(utf);
    int rc = 0;
    if (n + 1 > cap) rc = -1;
    else memcpy(buf, utf, n + 1);
    (*env)->ReleaseStringUTFChars(env, value, utf);
    return rc;
}

static int *list_open_fds(int *count) {
    DIR *dir = opendir("/proc/self/fd");
    if (dir == NULL) {
        *count = 0;
        return NULL;
    }
    int dir_fd = dirfd(dir);
    int cap = 64;
    int n = 0;
    int *fds = (int *) malloc((size_t) cap * sizeof(int));
    if (fds == NULL) {
        closedir(dir);
        *count = 0;
        return NULL;
    }
    struct dirent *ent;
    while ((ent = readdir(dir)) != NULL) {
        if (ent->d_name[0] == '.') continue;
        int fd = (int) atoi(ent->d_name);
        if (fd == dir_fd) continue;
        if (n == cap) {
            cap *= 2;
            int *grown = (int *) realloc(fds, (size_t) cap * sizeof(int));
            if (grown == NULL) break;
            fds = grown;
        }
        fds[n++] = fd;
    }
    closedir(dir);
    *count = n;
    return fds;
}

static char **build_env(const char *home, char **owned) {
    int n = 0;
    if (environ != NULL) {
        for (char **it = environ; *it != NULL; ++it) n++;
    }
    char **envp = (char **) calloc((size_t) n + 6, sizeof(char *));
    if (envp == NULL) return NULL;
    int j = 0;
    int has_lang = 0;
    int has_path = 0;
    for (int i = 0; i < n; i++) {
        char *entry = environ[i];
        if (strncmp(entry, "HOME=", 5) == 0) continue;
        if (strncmp(entry, "TERM=", 5) == 0) continue;
        if (strncmp(entry, "SHELL=", 6) == 0) continue;
        if (strncmp(entry, "LANG=", 5) == 0) has_lang = 1;
        if (strncmp(entry, "PATH=", 5) == 0) has_path = 1;
        envp[j++] = entry;
    }
    size_t home_len = strlen(home);
    char *home_env = (char *) malloc(home_len + 6);
    if (home_env == NULL) {
        free(envp);
        return NULL;
    }
    memcpy(home_env, "HOME=", 5);
    memcpy(home_env + 5, home, home_len + 1);
    *owned = home_env;
    envp[j++] = home_env;
    envp[j++] = (char *) "TERM=xterm-256color";
    envp[j++] = (char *) "SHELL=/system/bin/sh";
    if (!has_lang) envp[j++] = (char *) "LANG=C.UTF-8";
    if (!has_path) {
        envp[j++] = (char *) "PATH=/system/bin:/product/bin:/system_ext/bin:/vendor/bin";
    }
    envp[j] = NULL;
    return envp;
}

/* Дочерний процесс после fork: только async-signal-safe вызовы, затем сразу execve.
   Иначе мьютексы, которые держали другие потоки приложения, останутся занятыми. */
__attribute__((noreturn))
static void child_exec(int master, const char *slave_path, const char *cwd,
                        char **envp, const int *fds, int fd_count) {
    if (master >= 0) close(master);
    if (setsid() < 0) _exit(111);
    /* Маска сигналов копируется от потока, который вызвал fork. В ART SIGWINCH
       часто заблокирован — оболочка тогда не узнает о смене размера. */
    sigset_t empty;
    sigemptyset(&empty);
    sigprocmask(SIG_SETMASK, &empty, NULL);
    /* Без O_NOCTTY: процесс уже лидер сессии, slave становится управляющим терминалом. */
    int slave = open(slave_path, O_RDWR);
    if (slave < 0) _exit(112);
    /* EPERM значит, что open уже назначил controlling tty. */
    (void) ioctl(slave, TIOCSCTTY, 0);
    if (dup2(slave, STDIN_FILENO) < 0) _exit(113);
    if (dup2(slave, STDOUT_FILENO) < 0) _exit(114);
    if (dup2(slave, STDERR_FILENO) < 0) _exit(115);
    if (fds != NULL) {
        for (int i = 0; i < fd_count; i++) {
            int fd = fds[i];
            if (fd > STDERR_FILENO && fd != slave) close(fd);
        }
    } else {
        for (int fd = STDERR_FILENO + 1; fd < 256; fd++) {
            if (fd != slave) close(fd);
        }
    }
    if (slave > STDERR_FILENO) close(slave);
    if (cwd != NULL && cwd[0] != '\0') (void) chdir(cwd);
    char *argv[] = {(char *) "-sh", (char *) "-i", NULL};
    execve("/system/bin/sh", argv, envp);
    _exit(127);
}

static int configure_slave(const char *slave_path, int rows, int cols) {
    int slave = open(slave_path, O_RDWR | O_NOCTTY | O_CLOEXEC);
    if (slave < 0) return -1;
    struct termios tio;
    if (tcgetattr(slave, &tio) == 0) {
        tio.c_iflag |= IUTF8;
        tio.c_cc[VERASE] = 0x7F;
        tcsetattr(slave, TCSANOW, &tio);
    }
    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_row = (unsigned short) rows;
    ws.ws_col = (unsigned short) cols;
    /* Размер на slave до exec, чтобы первый prompt уже знал колонки. */
    ioctl(slave, TIOCSWINSZ, &ws);
    close(slave);
    return 0;
}

JNIEXPORT jintArray JNICALL
Java_client_wexmanager_feature_terminal_Pty_nativeOpen(
        JNIEnv *env, jclass clazz, jstring cwd, jstring home, jint rows, jint cols) {
    (void) clazz;
    if (cwd == NULL || home == NULL) {
        throw_io(env, "путь не задан");
        return NULL;
    }
    char cwd_buf[4096];
    char home_buf[4096];
    if (copy_jstring(env, cwd, cwd_buf, sizeof(cwd_buf)) != 0) {
        if (!(*env)->ExceptionCheck(env)) throw_io(env, "cwd слишком длинный");
        return NULL;
    }
    if (copy_jstring(env, home, home_buf, sizeof(home_buf)) != 0) {
        if (!(*env)->ExceptionCheck(env)) throw_io(env, "HOME слишком длинный");
        return NULL;
    }
    if (cwd_buf[0] == '\0') {
        cwd_buf[0] = '/';
        cwd_buf[1] = '\0';
    }
    int row_n = rows < 1 ? 1 : (rows > 1000 ? 1000 : rows);
    int col_n = cols < 1 ? 1 : (cols > 1000 ? 1000 : cols);

    int master = posix_openpt(O_RDWR | O_CLOEXEC);
    if (master < 0) {
        throw_errno(env, "posix_openpt");
        return NULL;
    }
    if (grantpt(master) < 0 || unlockpt(master) < 0) {
        throw_errno(env, "grantpt");
        close(master);
        return NULL;
    }
    unsigned int pty_no = 0;
    if (ioctl(master, TIOCGPTN, &pty_no) < 0) {
        throw_errno(env, "TIOCGPTN");
        close(master);
        return NULL;
    }
    char slave_path[64];
    snprintf(slave_path, sizeof(slave_path), "/dev/pts/%u", pty_no);
    if (configure_slave(slave_path, row_n, col_n) != 0) {
        throw_errno(env, "slave");
        close(master);
        return NULL;
    }
    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_row = (unsigned short) row_n;
    ws.ws_col = (unsigned short) col_n;
    ioctl(master, TIOCSWINSZ, &ws);

    char *owned = NULL;
    char **envp = build_env(home_buf, &owned);
    if (envp == NULL) {
        throw_io(env, "не хватило памяти для окружения");
        close(master);
        return NULL;
    }
    int fd_count = 0;
    int *fds = list_open_fds(&fd_count);

    pid_t pid = fork();
    if (pid < 0) {
        throw_errno(env, "fork");
        free(fds);
        free(owned);
        free(envp);
        close(master);
        return NULL;
    }
    if (pid == 0) {
        child_exec(master, slave_path, cwd_buf, envp, fds, fd_count);
    }
    free(fds);
    free(owned);
    free(envp);

    jintArray result = (*env)->NewIntArray(env, 2);
    if (result == NULL) {
        kill(-pid, SIGKILL);
        waitpid(pid, NULL, 0);
        close(master);
        return NULL;
    }
    jint values[2] = {master, (jint) pid};
    (*env)->SetIntArrayRegion(env, result, 0, 2, values);
    return result;
}

JNIEXPORT void JNICALL
Java_client_wexmanager_feature_terminal_Pty_nativeSetSize(
        JNIEnv *env, jclass clazz, jint fd, jint rows, jint cols) {
    (void) clazz;
    if (fd < 0) return;
    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_row = (unsigned short) (rows < 1 ? 1 : rows);
    ws.ws_col = (unsigned short) (cols < 1 ? 1 : cols);
    /* TIOCSWINSZ на мастере: ядро само шлёт SIGWINCH передней группе. */
    if (ioctl(fd, TIOCSWINSZ, &ws) < 0) throw_errno(env, "TIOCSWINSZ");
}

static int ppid_of(pid_t pid) {
    char path[64];
    snprintf(path, sizeof(path), "/proc/%d/status", (int) pid);
    FILE *file = fopen(path, "re");
    if (file == NULL) return -1;
    char line[128];
    int ppid = -1;
    while (fgets(line, sizeof(line), file) != NULL) {
        if (strncmp(line, "PPid:", 5) == 0) {
            ppid = atoi(line + 5);
            break;
        }
    }
    fclose(file);
    return ppid;
}

static void kill_session(pid_t sid, int master_fd) {
    pid_t foreground = 0;
    if (master_fd >= 0 && ioctl(master_fd, TIOCGPGRP, &foreground) < 0) foreground = 0;
    if (foreground > 1) kill(-foreground, SIGKILL);

    DIR *proc = opendir("/proc");
    if (proc != NULL) {
        struct dirent *ent;
        while ((ent = readdir(proc)) != NULL) {
            pid_t pid = (pid_t) atoi(ent->d_name);
            if (pid <= 1 || pid == sid) continue;
            char path[64];
            snprintf(path, sizeof(path), "/proc/%d/stat", (int) pid);
            FILE *file = fopen(path, "re");
            if (file == NULL) continue;
            char buf[1024];
            if (fgets(buf, sizeof(buf), file) == NULL) {
                fclose(file);
                continue;
            }
            fclose(file);
            char *paren = strrchr(buf, ')');
            if (paren == NULL) continue;
            char state = 0;
            int ppid = 0;
            int pgrp = 0;
            int session = 0;
            if (sscanf(paren + 1, " %c %d %d %d", &state, &ppid, &pgrp, &session) == 4
                    && session == (int) sid) {
                kill(pid, SIGKILL);
            }
        }
        closedir(proc);
    }
    kill(-sid, SIGKILL);
}

JNIEXPORT void JNICALL
Java_client_wexmanager_feature_terminal_Pty_nativeKillGroup(
        JNIEnv *env, jclass clazz, jint pid, jint fd) {
    (void) env;
    (void) clazz;
    if (pid <= 0) return;
    /* pid после waitpid могут отдать другому процессу — не трогаем чужую группу. */
    if (ppid_of((pid_t) pid) != getpid()) {
        int status = 0;
        pid_t reaped;
        do {
            reaped = waitpid((pid_t) pid, &status, WNOHANG);
        } while (reaped < 0 && errno == EINTR);
        return;
    }
    kill_session((pid_t) pid, fd);
    int status = 0;
    pid_t reaped;
    do {
        reaped = waitpid((pid_t) pid, &status, 0);
    } while (reaped < 0 && errno == EINTR);
}

JNIEXPORT void JNICALL
Java_client_wexmanager_feature_terminal_Pty_nativeClose(JNIEnv *env, jclass clazz, jint fd) {
    (void) env;
    (void) clazz;
    if (fd >= 0) close(fd);
}
