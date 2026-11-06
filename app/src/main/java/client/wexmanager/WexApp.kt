package client.wexmanager

import android.app.Application
import android.content.Context
import android.os.Build
import client.wexmanager.core.icons.ApkIconFetcher
import client.wexmanager.core.icons.AudioCoverFetcher
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import coil3.video.VideoFrameDecoder

class WexApp : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        client.wexmanager.feature.search.Ripgrep.init(this)
        client.wexmanager.core.archive.Unrar.init(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(ApkIconFetcher.Factory())
                add(ApkIconFetcher.Key())
                add(AudioCoverFetcher.Factory())
                add(AudioCoverFetcher.Key())
                add(client.wexmanager.core.icons.PdfThumbFetcher.Factory())
                add(client.wexmanager.core.icons.PdfThumbFetcher.Key())
                add(client.wexmanager.core.icons.FontThumbFetcher.Factory())
                add(client.wexmanager.core.icons.FontThumbFetcher.Key())
                add(VideoFrameDecoder.Factory())
                add(SvgDecoder.Factory())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) add(AnimatedImageDecoder.Factory()) else add(GifDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder().maxSizePercent(context, 0.2).build()
            }
            .crossfade(180)
            .build()
}

val Context.appContainer: AppContainer get() = (applicationContext as WexApp).container
