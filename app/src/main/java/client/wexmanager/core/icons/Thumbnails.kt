package client.wexmanager.core.icons

import android.media.MediaMetadataRetriever
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import okio.Buffer
import okio.FileSystem

/** Исходник больше этого не декодируем: тот же null, что и при ошибке. */
private const val MAX_DECODE_BYTES = 48L * 1024 * 1024

private fun sourceTooLarge(path: String): Boolean =
    runCatching { java.io.File(path).length() > MAX_DECODE_BYTES }.getOrDefault(true)

/** Модель для загрузки иконки приложения из APK-файла. */
data class ApkIconModel(val path: String, val lastModified: Long)

/** Модель для загрузки встроенной обложки аудиофайла. */
data class AudioCoverModel(val path: String, val lastModified: Long)

class ApkIconFetcher(private val data: ApkIconModel, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        if (sourceTooLarge(data.path)) return null
        val pm = options.context.packageManager
        val info = pm.getPackageArchiveInfo(data.path, 0) ?: return null
        val app = info.applicationInfo ?: return null
        app.sourceDir = data.path
        app.publicSourceDir = data.path
        val drawable = app.loadIcon(pm) ?: return null
        return ImageFetchResult(image = drawable.asImage(), isSampled = false, dataSource = DataSource.DISK)
    }

    class Factory : Fetcher.Factory<ApkIconModel> {
        override fun create(data: ApkIconModel, options: Options, imageLoader: ImageLoader): Fetcher =
            ApkIconFetcher(data, options)
    }

    class Key : Keyer<ApkIconModel> {
        override fun key(data: ApkIconModel, options: Options) = "apk:${data.path}:${data.lastModified}"
    }
}

class AudioCoverFetcher(private val data: AudioCoverModel) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        if (sourceTooLarge(data.path)) return null
        val bytes = MediaMetadataRetriever().let { mmr ->
            try {
                mmr.setDataSource(data.path)
                mmr.embeddedPicture
            } catch (_: Exception) {
                null
            } finally {
                mmr.release()
            }
        } ?: return null
        return SourceFetchResult(
            source = ImageSource(Buffer().write(bytes), FileSystem.SYSTEM),
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<AudioCoverModel> {
        override fun create(data: AudioCoverModel, options: Options, imageLoader: ImageLoader): Fetcher =
            AudioCoverFetcher(data)
    }

    class Key : Keyer<AudioCoverModel> {
        override fun key(data: AudioCoverModel, options: Options) = "cover:${data.path}:${data.lastModified}"
    }
}

/** Миниатюра PDF — первая страница. */
data class PdfThumbModel(val path: String, val lastModified: Long)

class PdfThumbFetcher(private val data: PdfThumbModel, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        if (sourceTooLarge(data.path)) return null
        val target = (options.size.width as? coil3.size.Dimension.Pixels)?.px?.coerceIn(64, 512) ?: 256
        val bitmap = runCatching {
            android.os.ParcelFileDescriptor.open(java.io.File(data.path), android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                android.graphics.pdf.PdfRenderer(fd).use { renderer ->
                    if (renderer.pageCount == 0) return null
                    renderer.openPage(0).use { page ->
                        val h = (target.toFloat() * page.height / page.width.coerceAtLeast(1)).toInt().coerceIn(1, target * 3)
                        android.graphics.Bitmap.createBitmap(target, h, android.graphics.Bitmap.Config.ARGB_8888).also { bmp ->
                            bmp.eraseColor(android.graphics.Color.WHITE) // страницы PDF прозрачные
                            page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }
        }.getOrNull() ?: return null
        return ImageFetchResult(image = bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    class Factory : Fetcher.Factory<PdfThumbModel> {
        override fun create(data: PdfThumbModel, options: Options, imageLoader: ImageLoader): Fetcher = PdfThumbFetcher(data, options)
    }

    class Key : Keyer<PdfThumbModel> {
        override fun key(data: PdfThumbModel, options: Options) = "pdf:${data.path}:${data.lastModified}"
    }
}

/** Миниатюра шрифта — «Аа» этим шрифтом. */
data class FontThumbModel(val path: String, val lastModified: Long)

class FontThumbFetcher(private val data: FontThumbModel, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        if (sourceTooLarge(data.path)) return null
        val size = (options.size.width as? coil3.size.Dimension.Pixels)?.px?.coerceIn(64, 256) ?: 128
        val typeface = runCatching { android.graphics.Typeface.createFromFile(data.path) }.getOrNull() ?: return null
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(0xFFF3F1F8.toInt())
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            color = 0xFF2A2638.toInt()
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = size * 0.48f
        }
        // Без кириллицы в шрифте «Аа» нарисовал бы системный шрифт — тогда показываем латиницу.
        val hasCyrillic = client.wexmanager.feature.viewer.FontInfo.coverage(java.io.File(data.path))?.invoke('А'.code) ?: true
        val sample = if (hasCyrillic) "Аа" else "Aa"
        // Вертикально центрируем по реальным границам текста.
        val bounds = android.graphics.Rect().also { paint.getTextBounds(sample, 0, sample.length, it) }
        canvas.drawText(sample, size / 2f, size / 2f - bounds.exactCenterY(), paint)
        return ImageFetchResult(image = bitmap.asImage(), isSampled = false, dataSource = DataSource.DISK)
    }

    class Factory : Fetcher.Factory<FontThumbModel> {
        override fun create(data: FontThumbModel, options: Options, imageLoader: ImageLoader): Fetcher = FontThumbFetcher(data, options)
    }

    class Key : Keyer<FontThumbModel> {
        override fun key(data: FontThumbModel, options: Options) = "font:${data.path}:${data.lastModified}"
    }
}
