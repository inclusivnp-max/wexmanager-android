package client.wexmanager.core.fs

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import androidx.compose.runtime.Immutable
import java.io.File

@Immutable
data class StorageInfo(
    val path: String,
    val title: String,
    val isPrimary: Boolean,
    val isRemovable: Boolean,
    val total: Long,
    val free: Long,
) {
    val used: Long get() = total - free
    val usedFraction: Float get() = if (total > 0) used.toFloat() / total else 0f
}

class StorageRepository(private val context: Context) {

    val primaryPath: String = Environment.getExternalStorageDirectory().absolutePath

    /** Корень тома без статистики заполнения — дёшево, кешируется (для хлебных крошек и т.п.). */
    data class VolumeRoot(val path: String, val title: String, val isPrimary: Boolean)

    @Volatile private var rootsCache: List<VolumeRoot>? = null
    @Volatile private var rootsTime = 0L

    fun roots(): List<VolumeRoot> {
        val now = System.currentTimeMillis()
        rootsCache?.takeIf { now - rootsTime < 30_000 }?.let { return it }
        val sm = context.getSystemService(StorageManager::class.java)
        val list = sm.storageVolumes.mapNotNull { v ->
            if (v.state != Environment.MEDIA_MOUNTED && v.state != Environment.MEDIA_MOUNTED_READ_ONLY) return@mapNotNull null
            val path = volumePath(v) ?: return@mapNotNull null
            VolumeRoot(path, if (v.isPrimary) "Внутренняя память" else v.getDescription(context) ?: "Накопитель", v.isPrimary)
        }.ifEmpty { listOf(VolumeRoot(primaryPath, "Внутренняя память", true)) }
        rootsCache = list
        rootsTime = now
        return list
    }

    fun volumes(): List<StorageInfo> {
        val sm = context.getSystemService(StorageManager::class.java)
        val result = mutableListOf<StorageInfo>()
        for (v in sm.storageVolumes) {
            if (v.state != Environment.MEDIA_MOUNTED && v.state != Environment.MEDIA_MOUNTED_READ_ONLY) continue
            val path = volumePath(v) ?: continue
            val stat = runCatching { StatFs(path) }.getOrNull() ?: continue
            result += StorageInfo(
                path = path,
                title = if (v.isPrimary) "Внутренняя память" else v.getDescription(context) ?: "Накопитель",
                isPrimary = v.isPrimary,
                isRemovable = v.isRemovable,
                total = stat.totalBytes,
                free = stat.availableBytes,
            )
        }
        if (result.none { it.isPrimary }) {
            runCatching { StatFs(primaryPath) }.getOrNull()?.let {
                result.add(0, StorageInfo(primaryPath, "Внутренняя память", true, false, it.totalBytes, it.availableBytes))
            }
        }
        return result
    }

    fun rootInfo(): StorageInfo? = runCatching {
        val s = StatFs("/")
        StorageInfo("/", "Корень системы", false, false, s.totalBytes, s.availableBytes)
    }.getOrNull()

    fun infoFor(path: String): StorageInfo? {
        val volume = volumes().filter { path.startsWith(it.path) }.maxByOrNull { it.path.length }
        if (volume != null) return volume
        return runCatching {
            val s = StatFs(path)
            StorageInfo(path, path, false, false, s.totalBytes, s.availableBytes)
        }.getOrNull()
    }

    private fun volumePath(v: StorageVolume): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return v.directory?.absolutePath
        return runCatching {
            (StorageVolume::class.java.getMethod("getPath").invoke(v) as String)
        }.getOrNull()
    }

    data class QuickAccess(val title: String, val path: String, val kind: String)

    fun quickAccess(): List<QuickAccess> {
        val root = primaryPath
        return listOf(
            QuickAccess("Загрузки", "$root/${Environment.DIRECTORY_DOWNLOADS}", "download"),
            QuickAccess("Камера", "$root/${Environment.DIRECTORY_DCIM}", "camera"),
            QuickAccess("Изображения", "$root/${Environment.DIRECTORY_PICTURES}", "image"),
            QuickAccess("Документы", "$root/${Environment.DIRECTORY_DOCUMENTS}", "document"),
            QuickAccess("Музыка", "$root/${Environment.DIRECTORY_MUSIC}", "music"),
            QuickAccess("Видео", "$root/${Environment.DIRECTORY_MOVIES}", "video"),
            QuickAccess("Android/data", "$root/Android/data", "android"),
            QuickAccess("Android/obb", "$root/Android/obb", "android"),
            QuickAccess("Android/media", "$root/Android/media", "android"),
        ).filter {
            File(it.path).exists() ||
                it.path == "$root/Android/data" ||
                it.path == "$root/Android/obb" ||
                it.path == "$root/Android/media"
        }
    }
}
