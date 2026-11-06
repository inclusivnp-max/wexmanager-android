package client.wexmanager.feature.apk

import client.wexmanager.appContainer
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import client.wexmanager.core.apk.ApkAnalyzer
import client.wexmanager.core.apk.ApkDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ApkInfoState {
    data object Loading : ApkInfoState
    data class Ready(val details: ApkDetails) : ApkInfoState
    data class Error(val message: String) : ApkInfoState
}

class ApkInfoViewModel(app: Application, val path: String) : AndroidViewModel(app) {

    private val _state = MutableStateFlow<ApkInfoState>(ApkInfoState.Loading)
    val state: StateFlow<ApkInfoState> = _state.asStateFlow()

    private val _manifestBusy = MutableStateFlow(false)
    val manifestBusy: StateFlow<Boolean> = _manifestBusy.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun load() {
        loadJob?.cancel()
        _state.value = ApkInfoState.Loading
        loadJob = viewModelScope.launch {
            val app = getApplication<Application>()
            val iconPx = (app.resources.displayMetrics.density * 112).toInt().coerceIn(96, 512)
            _state.value = try {
                ApkInfoState.Ready(ApkAnalyzer.analyze(app, path, iconPx))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                ApkInfoState.Error(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /**
     * Текстовая сводка по полям, которые уже есть в состоянии.
     * null, пока анализ не завершился.
     */
    fun summaryText(): String? {
        val d = (_state.value as? ApkInfoState.Ready)?.details ?: return null
        val versionName = d.versionName?.takeIf { it.isNotBlank() }
        return buildString {
            append("Файл: ").appendLine(java.io.File(d.path).name)
            append("Пакет: ").appendLine(d.packageName.ifEmpty { "—" })
            if (versionName != null) {
                append("Версия: ").append(versionName).append(" (").append(d.versionCode).appendLine(")")
            } else {
                append("Код версии: ").appendLine(d.versionCode)
            }
            if (d.minSdk > 0) append("Мин. SDK: ").appendLine(d.minSdk)
            if (d.targetSdk > 0) append("Целевой SDK: ").appendLine(d.targetSdk)
            append("ABI: ").appendLine(if (d.abis.isEmpty()) "нет" else d.abis.joinToString(", ") { it.abi })
            append("Схемы подписи: ").appendLine(
                if (d.signatureSchemes.isEmpty()) "нет" else d.signatureSchemes.joinToString(", "),
            )
            append("Разрешений: ").appendLine(d.permissions.size)
            append("Активности: ").appendLine(d.activities.size)
            append("Сервисы: ").appendLine(d.services.size)
            append("Приёмники: ").appendLine(d.receivers.size)
            append("Провайдеры: ").appendLine(d.providers.size)
        }.trimEnd()
    }

    /** Перепроверить установленную версию (после установки/удаления пакета). */
    fun refreshInstalled() {
        val ready = _state.value as? ApkInfoState.Ready ?: return
        val pkg = ready.details.packageName.ifEmpty { return }
        viewModelScope.launch {
            val installed = withContext(Dispatchers.IO) {
                ApkAnalyzer.installedVersion(getApplication<Application>().packageManager, pkg)
            }
            _state.update { s ->
                if (s is ApkInfoState.Ready && s.details.installed != installed) s.copy(details = s.details.copy(installed = installed)) else s
            }
        }
    }

    /** Клон со сменой пакета рядом с APK (`<имя>_clone.apk`), затем подпись; результат — имя файла. */
    suspend fun cloneApk(newPackage: String): Result<String> = runCatching {
        val apk = java.io.File(path)
        // Установленное приложение (/data/app/…) — рядом писать нельзя: результат в «Загрузки/WexManager».
        @Suppress("DEPRECATION")
        val dir = apk.parentFile?.takeIf { it.canWrite() }
            ?: java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "WexManager").apply { mkdirs() }
        val stem = if (apk.name == "base.apk") (state.value as? ApkInfoState.Ready)?.details?.packageName ?: apk.nameWithoutExtension else apk.nameWithoutExtension
        var out = java.io.File(dir, stem + "_clone.apk")
        var n = 2
        while (out.exists()) out = java.io.File(dir, stem + "_clone ($n).apk").also { n++ }
        client.wexmanager.core.apk.ApkCloner.clone(apk, newPackage, out, java.io.File(getApplication<Application>().cacheDir, "clone-" + System.nanoTime()))
        val c = getApplication<Application>().appContainer
        c.taskManager.signApk(out, c.userData.data.value.activeSigningKey)
        (if (dir != apk.parentFile) "Загрузки/WexManager/" else "") + out.nameWithoutExtension + "_signed.apk"
    }

    /** Декодирует манифест в файл; возвращает путь или ошибку. */
    /**
     * Манифест для правки: бинарный AndroidManifest.xml извлекается во временную папку и расшифровывается;
     * редактор при сохранении соберёт его обратно и перепакует APK (если файл доступен для записи).
     */
    suspend fun exportManifest(): Result<client.wexmanager.ui.navigation.Route.Editor> {
        if (_manifestBusy.value) return Result.failure(IllegalStateException("Уже выполняется"))
        _manifestBusy.value = true
        return try {
            val apk = java.io.File(path)
            val binary = client.wexmanager.core.archive.ArchiveEngine.extractToCache(apk, "AndroidManifest.xml", getApplication<Application>().cacheDir)
            val text = client.wexmanager.core.apk.AxmlFiles.decodeToText(getApplication(), binary)
            val writable = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { apk.canWrite() }
            Result.success(
                client.wexmanager.ui.navigation.Route.Editor(
                    text.path,
                    archive = path.takeIf { writable },
                    entry = "AndroidManifest.xml".takeIf { writable },
                    readOnly = !writable,
                    lockReadOnly = !writable,
                    binarySource = binary.path,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        } finally {
            _manifestBusy.value = false
        }
    }
}
