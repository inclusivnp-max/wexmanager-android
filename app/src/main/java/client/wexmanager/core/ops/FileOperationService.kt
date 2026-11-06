package client.wexmanager.core.ops

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import client.wexmanager.MainActivity
import client.wexmanager.R
import client.wexmanager.appContainer
import client.wexmanager.core.util.Format
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/** Держит процесс живым, пока идут файловые операции, и показывает прогресс в уведомлении. */
class FileOperationService : LifecycleService() {

    private var started = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_CANCEL_ALL) {
            val tm = appContainer.taskManager
            tm.tasks.value.filter { it.isActive }.forEach { tm.cancel(it.id) }
        }
        val notification = buildNotification(appContainer.taskManager.tasks.value)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        if (!started) {
            started = true
            lifecycleScope.launch {
                appContainer.taskManager.tasks.debounce(250).collectLatest { tasks ->
                    if (tasks.none { it.isActive }) {
                        ServiceCompat.stopForeground(this@FileOperationService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        runCatching {
                            NotificationManagerCompat.from(this@FileOperationService)
                                .notify(NOTIFICATION_ID, buildNotification(tasks))
                        }
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    /** Android 15+: суточный лимит dataSync исчерпан — отменяем задачи и корректно завершаемся. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        val tm = appContainer.taskManager
        tm.tasks.value.filter { it.isActive }.forEach { tm.cancel(it.id) }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Файловые операции", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Прогресс копирования, перемещения, сжатия и распаковки"
                setShowBadge(false)
            }
        )
    }

    private fun buildNotification(tasks: List<TaskState>) = run {
        val active = tasks.filter { it.isActive }
        val main = active.firstOrNull()
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val cancel = PendingIntent.getService(
            this, 1,
            Intent(this, FileOperationService::class.java).setAction(ACTION_CANCEL_ALL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(main?.title ?: "Файловые операции")
            .setContentText(
                when {
                    main == null -> "Подготовка…"
                    main.status == TaskStatus.WaitingConflict -> "Требуется решение: файл уже существует"
                    main.totalBytes > 0 -> "${Format.size(main.processedBytes)} из ${Format.size(main.totalBytes)} • ${main.currentName}"
                    else -> main.currentName
                }
            )
            .setSubText(if (active.size > 1) "Задач: ${active.size}" else null)
            .setProgress(1000, ((main?.progress ?: 0f) * 1000).toInt(), main == null || main.status == TaskStatus.Preparing)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .addAction(0, "Отменить", cancel)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "file_ops"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_CANCEL_ALL = "client.wexmanager.CANCEL_ALL"
    }
}
