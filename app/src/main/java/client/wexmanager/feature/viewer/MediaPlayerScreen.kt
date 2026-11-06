package client.wexmanager.feature.viewer

import android.content.Context
import android.content.pm.ActivityInfo
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOn
import androidx.compose.material.icons.rounded.RepeatOneOn
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.graphics.shapes.Morph
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import client.wexmanager.BrowserRequest
import client.wexmanager.appContainer
import client.wexmanager.core.fs.FileItem
import client.wexmanager.core.fs.FileKind
import client.wexmanager.core.fs.LocalFileSystem
import client.wexmanager.core.icons.AudioCoverModel
import client.wexmanager.core.util.FileOpener
import client.wexmanager.core.util.Format
import client.wexmanager.ui.components.FileIcon
import client.wexmanager.ui.components.MorphShape
import client.wexmanager.ui.theme.LocalAppSettings
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.min

/**
 * Плеер аудио и видео на Media3 ExoPlayer: плейлист из соседних файлов папки, свой UI в стиле
 * M3 Expressive (волнистый прогресс, морфинг кнопки воспроизведения, скорость, повтор, таймер сна).
 */
@Composable
fun MediaPlayerScreen(path: String, onBack: () -> Unit) {
    val settings = LocalAppSettings.current
    val appContext = LocalContext.current.applicationContext
    // Заглушка без обращения к диску (тип — по имени); размер и дата подгружаются в фоне.
    val startItem = remember(path) {
        FileItem(path = path, name = path.substringAfterLast('/'), isDirectory = false, size = 0L, lastModified = 0L)
    }
    val resumeMs = remember(path) { MediaPositions.peek(appContext, path) }
    val persistEnabled = remember(path) { AtomicBoolean(resumeMs <= RESUME_MIN_MS) }
    val player = rememberExoPlayer(startItem, resumeMs)
    val ui = rememberPlayerUi(player)
    var playlist by remember(path) { mutableStateOf(listOf(startItem)) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val sleep = remember(player) { SleepTimer() }
    SleepTimerEffect(player, sleep, snackbar, scope)

    // Дозагружаем соседние файлы того же типа: вставляем их вокруг текущего, не прерывая воспроизведение.
    LaunchedEffect(player) {
        val stat = withContext(Dispatchers.IO) { LocalFileSystem.stat(path) }
        if (playlist.size == 1) playlist = listOf(stat)
        val kind = stat.kind
        if (kind != FileKind.Audio && kind != FileKind.Video) return@LaunchedEffect
        val list = try {
            loadSiblings(path, settings.showHidden || stat.isHidden) { it == kind }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return@LaunchedEffect
        }
        val idx = list.indexOfFirst { it.path == path }
        if (list.size <= 1 || idx < 0) return@LaunchedEffect
        player.addMediaItems(0, list.subList(0, idx).map(::mediaItemOf))
        player.addMediaItems(list.subList(idx + 1, list.size).map(::mediaItemOf))
        playlist = list
        ui.sync(player)
    }

    // Продолжаем с сохранённой позиции, если она дальше 5 с и не у самого конца.
    LaunchedEffect(player) {
        if (resumeMs <= RESUME_MIN_MS) {
            persistEnabled.set(true)
            return@LaunchedEffect
        }
        var userTookOver = false
        val listener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) userTookOver = true
            }
        }
        player.addListener(listener)
        try {
            var spins = 0
            while (player.playerError == null && spins < 200) {
                val dur = player.duration
                val known = dur != C.TIME_UNSET && dur > 0L
                val state = player.playbackState
                if (known || state == Player.STATE_ENDED) {
                    val stillThisFile = player.currentMediaItem?.mediaId == path
                    val nearEnd = known && resumeMs >= dur - RESUME_END_MARGIN_MS
                    if (stillThisFile && !nearEnd && state != Player.STATE_ENDED) player.seekTo(resumeMs)
                    if (!userTookOver) player.play()
                    break
                }
                spins++
                delay(50)
            }
            if (!userTookOver && player.playerError == null && !player.playWhenReady) player.play()
        } finally {
            player.removeListener(listener)
            persistEnabled.set(true)
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        player.pause()
        savePlaybackPosition(appContext, player, persistEnabled)
    }

    // Позиция обновляется только пока экран виден; туда же пишется media-positions.json.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(player, lifecycle) {
        var trackedId: String? = null
        var trackedPos = 0L
        var savedId: String? = null
        var savedPos = Long.MIN_VALUE
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                ui.tick(player)
                val id = player.currentMediaItem?.mediaId
                val pos = player.currentPosition.coerceAtLeast(0L)
                val dur = player.duration
                if (persistEnabled.get()) {
                    val previousId = trackedId
                    if (previousId != null && id != previousId && worthSaving(trackedPos, C.TIME_UNSET)) {
                        val previousPos = trackedPos
                        withContext(Dispatchers.IO) { MediaPositions.save(appContext, previousId, previousPos) }
                    }
                    if (id != null && worthSaving(pos, dur)) {
                        val stepped = savedPos == Long.MIN_VALUE || id != savedId || abs(pos - savedPos) >= POSITION_SAVE_STEP_MS
                        val pausedCatchUp = !player.isPlaying && (savedPos == Long.MIN_VALUE || abs(pos - savedPos) >= 500L)
                        if (stepped || pausedCatchUp) {
                            withContext(Dispatchers.IO) { MediaPositions.save(appContext, id, pos) }
                            savedId = id
                            savedPos = pos
                        }
                    }
                }
                trackedId = id
                trackedPos = pos
                delay(250)
            }
        }
    }
    DisposableEffect(player) {
        onDispose { savePlaybackPosition(appContext, player, persistEnabled) }
    }

    val current = playlist.getOrNull(ui.index) ?: playlist.first()
    val controller = remember(player, ui) { PlayerActions(player, ui) }
    val isVideo = current.kind == FileKind.Video || (current.kind != FileKind.Audio && ui.hasVideo)
    if (isVideo) {
        ViewerDarkTheme {
            VideoPlayerContent(player, ui, controller, playlist, current, onBack, snackbar, sleep)
        }
    } else {
        AudioPlayerContent(ui, controller, playlist, current, onBack, snackbar, sleep)
    }
}

private fun mediaItemOf(item: FileItem): MediaItem =
    MediaItem.Builder().setUri(Uri.fromFile(File(item.path))).setMediaId(item.path).build()

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun rememberExoPlayer(item: FileItem, resumeMs: Long): ExoPlayer {
    val appContext = LocalContext.current.applicationContext
    val player = remember(item.path) {
        val attrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(if (item.kind == FileKind.Video) C.AUDIO_CONTENT_TYPE_MOVIE else C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        ExoPlayer.Builder(appContext)
            .setSeekBackIncrementMs(SEEK_STEP_MS)
            .setSeekForwardIncrementMs(SEEK_STEP_MS)
            .setAudioAttributes(attrs, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                setMediaItem(mediaItemOf(item))
                prepare()
                // Пока не решим, восстанавливать ли позицию, не стартуем с нуля.
                playWhenReady = resumeMs <= RESUME_MIN_MS
            }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    return player
}

private const val SEEK_STEP_MS = 10_000L
private const val RESUME_MIN_MS = 5_000L
private const val RESUME_END_MARGIN_MS = 5_000L
private const val POSITION_SAVE_STEP_MS = 5_000L

private fun worthSaving(positionMs: Long, durationMs: Long): Boolean {
    if (positionMs > RESUME_MIN_MS) return true
    if (durationMs == C.TIME_UNSET || durationMs <= 0L) return false
    return positionMs > 0L && positionMs >= durationMs - RESUME_END_MARGIN_MS
}

private fun savePlaybackPosition(context: Context, player: Player, enabled: AtomicBoolean) {
    if (!enabled.get()) return
    val id = player.currentMediaItem?.mediaId ?: return
    val pos = player.currentPosition.coerceAtLeast(0L)
    if (!worthSaving(pos, player.duration)) return
    MediaPositions.save(context, id, pos)
}

/** Последняя позиция по пути. Файл filesDir/media-positions.json, не больше 100 записей. */
private object MediaPositions {
    private const val FILE_NAME = "media-positions.json"
    private const val CAP = 100
    private val lock = Any()

    fun peek(context: Context, path: String): Long = synchronized(lock) {
        read(context).lastOrNull { it.first == path }?.second ?: 0L
    }

    fun save(context: Context, path: String, positionMs: Long) {
        if (path.isEmpty() || positionMs < 0L) return
        synchronized(lock) {
            val items = read(context).filterNot { it.first == path }.toMutableList()
            items.add(path to positionMs)
            while (items.size > CAP) items.removeAt(0)
            write(context, items)
        }
    }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    private fun read(context: Context): List<Pair<String, Long>> {
        val text = runCatching { file(context).readText() }.getOrNull().orEmpty()
        if (text.isBlank()) return emptyList()
        val array = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
        val out = ArrayList<Pair<String, Long>>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val path = obj.optString("path")
            if (path.isEmpty()) continue
            val position = obj.optLong("position", -1L)
            if (position < 0L) continue
            out.add(path to position)
        }
        return out
    }

    private fun write(context: Context, items: List<Pair<String, Long>>) {
        val array = JSONArray()
        for ((path, position) in items) {
            array.put(JSONObject().put("path", path).put("position", position))
        }
        val text = array.toString()
        val dest = file(context)
        val tmp = File(context.filesDir, "$FILE_NAME.tmp")
        runCatching {
            tmp.writeText(text)
            if (dest.exists() && !dest.delete()) {
                dest.writeText(text)
                tmp.delete()
            } else if (!tmp.renameTo(dest)) {
                dest.writeText(text)
                tmp.delete()
            }
        }
    }
}

private enum class SleepMode(val label: String, val shortLabel: String, val delayMs: Long?) {
    Min15("15 минут", "15 мин", 15 * 60_000L),
    Min30("30 минут", "30 мин", 30 * 60_000L),
    Min45("45 минут", "45 мин", 45 * 60_000L),
    EndOfTrack("Конец трека", "Трек", null),
}

@Stable
private class SleepTimer {
    var mode by mutableStateOf<SleepMode?>(null)
        private set
    var generation by mutableIntStateOf(0)
        private set

    fun select(next: SleepMode) {
        mode = next
        generation++
    }

    fun cancel() {
        mode = null
        generation++
    }

    fun clear() {
        mode = null
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun SleepTimerEffect(
    player: ExoPlayer,
    timer: SleepTimer,
    snackbar: SnackbarHostState,
    scope: CoroutineScope,
) {
    val mode = timer.mode
    val generation = timer.generation
    LaunchedEffect(mode, generation) {
        val delayMs = mode?.delayMs ?: return@LaunchedEffect
        delay(delayMs)
        if (timer.mode != mode || timer.generation != generation) return@LaunchedEffect
        player.pause()
        timer.clear()
        scope.launch { snackbar.showSnackbar("Пауза: таймер сна", duration = SnackbarDuration.Short) }
    }
    DisposableEffect(mode, generation) {
        if (mode != SleepMode.EndOfTrack) return@DisposableEffect onDispose {}
        player.pauseAtEndOfMediaItems = true
        var done = false
        fun fire() {
            if (done || timer.mode != SleepMode.EndOfTrack || timer.generation != generation) return
            done = true
            player.pause()
            timer.clear()
            scope.launch { snackbar.showSnackbar("Пауза: конец трека", duration = SnackbarDuration.Short) }
        }
        val listener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) fire()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) fire()
            }
        }
        player.addListener(listener)
        if (player.playbackState == Player.STATE_ENDED) fire()
        onDispose {
            player.removeListener(listener)
            player.pauseAtEndOfMediaItems = false
        }
    }
}

/** Снимок состояния плеера для Compose. Обновляется слушателем и тикером позиции. */
@Stable
private class PlayerUi {
    var playWhenReady by mutableStateOf(true)
    var isPlaying by mutableStateOf(false)
    var buffering by mutableStateOf(false)
    var ended by mutableStateOf(false)
    var index by mutableIntStateOf(0)
    var count by mutableIntStateOf(1)
    var hasVideo by mutableStateOf(false)
    var hasNext by mutableStateOf(false)
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
    var speed by mutableFloatStateOf(1f)
    var error by mutableStateOf<PlaybackException?>(null)
    var position by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)

    val showPause: Boolean get() = playWhenReady && !ended && error == null

    fun sync(p: Player) {
        playWhenReady = p.playWhenReady
        isPlaying = p.isPlaying
        buffering = p.playbackState == Player.STATE_BUFFERING
        ended = p.playbackState == Player.STATE_ENDED
        index = p.currentMediaItemIndex
        count = p.mediaItemCount
        hasVideo = p.currentTracks.containsType(C.TRACK_TYPE_VIDEO)
        hasNext = p.hasNextMediaItem()
        repeatMode = p.repeatMode
        speed = p.playbackParameters.speed
        error = p.playerError
        tick(p)
    }

    fun tick(p: Player) {
        position = p.currentPosition.coerceAtLeast(0L)
        val d = p.duration
        duration = if (d == C.TIME_UNSET || d < 0) 0L else d
    }
}

@Composable
private fun rememberPlayerUi(player: Player): PlayerUi {
    val ui = remember(player) { PlayerUi() }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = ui.sync(player)
        }
        player.addListener(listener)
        ui.sync(player)
        onDispose { player.removeListener(listener) }
    }
    return ui
}

/** Действия управления — общие для аудио и видео. */
private class PlayerActions(private val player: Player, private val ui: PlayerUi) {
    fun togglePlay() {
        if (ui.showPause) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(player.currentMediaItemIndex, 0L)
            player.play()
        }
    }

    fun seekTo(ms: Long) {
        player.seekTo(ms)
        ui.position = ms
    }

    fun back() {
        player.seekBack()
        ui.tick(player)
    }

    fun forward() {
        player.seekForward()
        ui.tick(player)
    }

    fun previous() = player.seekToPrevious()
    fun next() = player.seekToNext()

    fun select(index: Int) {
        if (index != player.currentMediaItemIndex && index in 0 until player.mediaItemCount) {
            player.seekTo(index, 0L)
            player.play()
        }
    }

    fun cycleRepeat() {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setSpeed(speed: Float) = player.setPlaybackSpeed(speed)

    fun retry() {
        player.prepare()
        player.play()
    }
}

private val Speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

private fun speedLabel(s: Float): String {
    val text = if (s == s.toInt().toFloat()) s.toInt().toString() else "%.2f".format(Format.RU, s).trimEnd('0')
    return "$text×"
}

private fun errorText(e: PlaybackException): String = when (e.errorCode) {
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> "Кодек не поддерживается этим устройством"
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> "Формат файла не поддерживается или файл повреждён"
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "Файл не найден"
    PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "Нет доступа к файлу"
    PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED -> "Не удалось инициализировать аудиовыход"
    else -> e.message ?: e.errorCodeName
}

// ------------------------------------------------------------------ Метаданные трека

private class TrackMeta(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val year: String? = null,
    val bitrate: Int = 0,
    val sampleRate: Int = 0,
)

private fun readTrackMeta(path: String): TrackMeta {
    val r = MediaMetadataRetriever()
    return try {
        r.setDataSource(path)
        fun get(key: Int) = r.extractMetadata(key)?.trim()?.takeIf { it.isNotEmpty() }
        TrackMeta(
            title = get(MediaMetadataRetriever.METADATA_KEY_TITLE),
            artist = get(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: get(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
            album = get(MediaMetadataRetriever.METADATA_KEY_ALBUM),
            year = get(MediaMetadataRetriever.METADATA_KEY_YEAR),
            bitrate = get(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0,
            sampleRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                get(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: 0
            } else 0,
        )
    } catch (_: Exception) {
        TrackMeta()
    } finally {
        runCatching { r.release() }
    }
}

// ------------------------------------------------------------------ Аудио

@Composable
private fun AudioPlayerContent(
    ui: PlayerUi,
    actions: PlayerActions,
    playlist: List<FileItem>,
    current: FileItem,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
    sleep: SleepTimer,
) {
    val scheme = MaterialTheme.colorScheme
    var queueOpen by remember { mutableStateOf(false) }
    val meta by produceState(TrackMeta(), current.path) {
        value = TrackMeta()
        value = withContext(Dispatchers.IO) { readTrackMeta(current.path) }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(scheme.surface)
            .background(
                Brush.verticalGradient(
                    listOf(scheme.primary.copy(alpha = 0.22f), scheme.surfaceContainerLow.copy(alpha = 0.35f), scheme.surface),
                ),
            ),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = scheme.onSurface,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Сейчас играет", style = MaterialTheme.typography.titleMedium)
                            Text(
                                File(current.path).parentFile?.name ?: "",
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад")
                        }
                    },
                    actions = {
                        if (playlist.size > 1) {
                            IconButton(onClick = { queueOpen = true }, shapes = IconButtonDefaults.shapes()) {
                                Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Очередь")
                            }
                        }
                        MediaMoreMenu(current, onBack)
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { inner ->
            BoxWithConstraints(Modifier.fillMaxSize().padding(inner)) {
                val landscape = maxWidth > maxHeight && maxHeight < 560.dp
                if (landscape) {
                    val cover = min(maxHeight.value - 32f, maxWidth.value * 0.42f).dp
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CoverPager(playlist, ui, actions, Modifier.size(cover))
                        Spacer(Modifier.width(32.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                            AudioControls(ui, actions, meta, current, playlist.size, sleep, onQueue = { queueOpen = true })
                        }
                    }
                } else {
                    val cover = min(maxWidth.value - 48f, min(maxHeight.value * 0.46f, 400f)).coerceAtLeast(120f).dp
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.weight(1f))
                        CoverPager(playlist, ui, actions, Modifier.fillMaxWidth().height(cover))
                        Spacer(Modifier.height(28.dp))
                        AudioControls(ui, actions, meta, current, playlist.size, sleep, onQueue = { queueOpen = true })
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
    if (queueOpen) {
        QueueSheet(playlist, ui.index, onSelect = { actions.select(it) }, onDismiss = { queueOpen = false })
    }
}

@Composable
private fun ColumnScope.AudioControls(
    ui: PlayerUi,
    actions: PlayerActions,
    meta: TrackMeta,
    current: FileItem,
    count: Int,
    sleep: SleepTimer,
    onQueue: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val scheme = MaterialTheme.colorScheme
    AnimatedContent(
        targetState = Triple(current.path, meta.title ?: current.baseName, listOfNotNull(meta.artist, meta.album).joinToString(" • ")),
        transitionSpec = {
            (fadeIn(motion.defaultEffectsSpec()) + slideInVertically(motion.defaultSpatialSpec()) { it / 3 })
                .togetherWith(fadeOut(motion.fastEffectsSpec()))
        },
        contentKey = { it },
        label = "trackTitle",
        modifier = Modifier.fillMaxWidth(),
    ) { (_, title, subtitle) ->
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.basicMarquee(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle.ifEmpty { "Неизвестный исполнитель" },
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.basicMarquee(),
            )
        }
    }
    val chips = buildList {
        add(current.extension.uppercase().ifEmpty { "АУДИО" })
        if (meta.bitrate > 0) add("${meta.bitrate / 1000} кбит/с")
        if (meta.sampleRate > 0) add("%.1f кГц".format(Format.RU, meta.sampleRate / 1000f))
        meta.year?.let { add(it) }
    }
    Spacer(Modifier.height(10.dp))
    Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        chips.forEach { InfoPill(it) }
    }
    val error = ui.error
    AnimatedVisibility(error != null) {
        Surface(
            color = scheme.errorContainer,
            contentColor = scheme.onErrorContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ErrorOutline, null)
                Spacer(Modifier.width(12.dp))
                Text(error?.let(::errorText) ?: "", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = actions::retry) { Text("Повторить") }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    SeekBar(ui, actions, wavy = true)
    Spacer(Modifier.height(12.dp))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(onClick = actions::previous, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.SkipPrevious, "Предыдущий", Modifier.size(28.dp))
        }
        IconButton(onClick = actions::back, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(52.dp)) {
            Icon(Icons.Rounded.Replay10, "Назад на 10 секунд", Modifier.size(28.dp))
        }
        PlayPauseButton(ui.showPause, onToggle = actions::togglePlay, height = 88.dp)
        IconButton(onClick = actions::forward, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(52.dp)) {
            Icon(Icons.Rounded.Forward10, "Вперёд на 10 секунд", Modifier.size(28.dp))
        }
        FilledTonalIconButton(
            onClick = actions::next,
            enabled = ui.hasNext,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(56.dp),
        ) {
            Icon(Icons.Rounded.SkipNext, "Следующий", Modifier.size(28.dp))
        }
    }
    Spacer(Modifier.height(20.dp))
    Surface(
        shape = CircleShape,
        color = scheme.surfaceContainerHigh,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    ) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            RepeatButton(ui.repeatMode, actions::cycleRepeat)
            SpeedButton(ui.speed, actions::setSpeed)
            SleepButton(sleep)
            if (count > 1) {
                TextButton(onClick = onQueue, shapes = androidx.compose.material3.ButtonDefaults.shapes()) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("${ui.index + 1} / $count")
                }
            }
        }
    }
}

@Composable
private fun InfoPill(text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

/** Обложки треков: свайп переключает трек, смена трека в плеере прокручивает пейджер. */
@Composable
private fun CoverPager(playlist: List<FileItem>, ui: PlayerUi, actions: PlayerActions, modifier: Modifier) {
    val pagerState = rememberPagerState(initialPage = ui.index.coerceIn(0, playlist.lastIndex)) { playlist.size }
    val index = ui.index
    LaunchedEffect(index, playlist.size) {
        if (index !in playlist.indices || pagerState.currentPage == index) return@LaunchedEffect
        if (abs(pagerState.currentPage - index) > 1) pagerState.scrollToPage(index) else pagerState.animateScrollToPage(index)
    }
    val currentIndex by rememberUpdatedState(index)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (page != currentIndex) actions.select(page)
        }
    }
    HorizontalPager(
        state = pagerState,
        key = { playlist[it].path },
        modifier = modifier,
        beyondViewportPageCount = 1,
    ) { page ->
        val item = playlist[page]
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val offset = pagerState.getOffsetDistanceInPages(page).absoluteValue.coerceIn(0f, 1f)
                    val s = lerp(1f, 0.82f, offset)
                    scaleX = s
                    scaleY = s
                    alpha = lerp(1f, 0.4f, offset)
                },
            contentAlignment = Alignment.Center,
        ) {
            CoverArt(
                item = item,
                playing = ui.isPlaying && page == index,
                modifier = Modifier.fillMaxHeight().aspectRatio(1f),
            )
        }
    }
}

@Composable
private fun CoverArt(item: FileItem, playing: Boolean, modifier: Modifier) {
    val motion = MaterialTheme.motionScheme
    var hasCover by remember(item.path) { mutableStateOf(false) }
    val scale by animateFloatAsState(if (playing) 1f else 0.9f, motion.slowSpatialSpec(), label = "coverScale")
    val corner by animateDpAsState(if (playing) 36.dp else 56.dp, motion.slowSpatialSpec(), label = "coverCorner")
    Box(
        modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        contentAlignment = Alignment.Center,
    ) {
        if (!hasCover) SpinningNote(playing, Modifier.fillMaxSize())
        AsyncImage(
            model = remember(item.path) { AudioCoverModel(item.path, item.lastModified) },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onState = { hasCover = it is AsyncImagePainter.State.Success },
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(corner)),
        )
    }
}

/** Заглушка без обложки: фигура MaterialShapes, медленно вращается и «распускается» при воспроизведении. */
@Composable
private fun SpinningNote(playing: Boolean, modifier: Modifier) {
    val motion = MaterialTheme.motionScheme
    val scheme = MaterialTheme.colorScheme
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        while (true) {
            rotation.snapTo(rotation.value % 360f)
            rotation.animateTo(rotation.value + 360f, tween(durationMillis = 24_000, easing = LinearEasing))
        }
    }
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie12Sided) }
    val progress by animateFloatAsState(if (playing) 1f else 0f, motion.slowSpatialSpec(), label = "noteMorph")
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize(0.94f)
                .graphicsLayer { rotationZ = rotation.value }
                .clip(MorphShape(morph, progress.coerceIn(0f, 1f)))
                .background(Brush.linearGradient(listOf(scheme.primaryContainer, scheme.tertiaryContainer))),
        )
        Icon(Icons.Rounded.MusicNote, null, tint = scheme.onPrimaryContainer, modifier = Modifier.fillMaxSize(0.36f))
    }
}

// ------------------------------------------------------------------ Общие элементы управления

@Composable
private fun SeekBar(ui: PlayerUi, actions: PlayerActions, wavy: Boolean, modifier: Modifier = Modifier) {
    val motion = MaterialTheme.motionScheme
    val state = remember { SliderState(0f) }
    var dragging by remember { mutableStateOf(false) }
    val duration = ui.duration
    val fraction = if (duration > 0) (ui.position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    LaunchedEffect(fraction, dragging) {
        if (!dragging) state.value = fraction
    }
    val amplitude by animateFloatAsState(
        if (ui.isPlaying && !dragging) 1f else 0f,
        motion.slowEffectsSpec(),
        label = "waveAmp",
    )
    Column(modifier.fillMaxWidth()) {
        Slider(
            state = state,
            enabled = duration > 0,
            onValueChange = {
                dragging = true
                state.value = it
            },
            onValueChangeFinished = {
                actions.seekTo((state.value * duration).toLong())
                dragging = false
            },
            track = { s ->
                if (wavy) {
                    LinearWavyProgressIndicator(
                        progress = { s.coercedValueAsFraction },
                        amplitude = { amplitude },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    SliderDefaults.Track(sliderState = s)
                }
            },
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            val shown = if (dragging) (state.value * duration).toLong() else ui.position
            Text(Format.duration(shown), style = MaterialTheme.typography.labelLarge, color = LocalContentColor.current.copy(alpha = 0.8f))
            Spacer(Modifier.weight(1f))
            Text(
                if (duration > 0) Format.duration(duration) else "--:--",
                style = MaterialTheme.typography.labelLarge,
                color = LocalContentColor.current.copy(alpha = 0.8f),
            )
        }
    }
}

/** Большая кнопка воспроизведения: круг на паузе ↔ скруглённый квадрат во время игры. */
@Composable
private fun PlayPauseButton(playing: Boolean, onToggle: () -> Unit, height: Dp) {
    val motion = MaterialTheme.motionScheme
    val scheme = MaterialTheme.colorScheme
    FilledIconToggleButton(
        checked = playing,
        onCheckedChange = { onToggle() },
        shapes = IconButtonDefaults.toggleableShapes(
            shape = RoundedCornerShape(50),
            pressedShape = RoundedCornerShape(height * 0.18f),
            checkedShape = RoundedCornerShape(height * 0.3f),
        ),
        colors = IconButtonDefaults.filledIconToggleButtonColors(
            containerColor = scheme.primary,
            contentColor = scheme.onPrimary,
            checkedContainerColor = scheme.primary,
            checkedContentColor = scheme.onPrimary,
        ),
        modifier = Modifier.size(width = height * 1.2f, height = height),
    ) {
        AnimatedContent(
            targetState = playing,
            transitionSpec = {
                (scaleIn(motion.fastSpatialSpec(), initialScale = 0.5f) + fadeIn(motion.fastEffectsSpec()))
                    .togetherWith(scaleOut(motion.fastSpatialSpec(), targetScale = 0.5f) + fadeOut(motion.fastEffectsSpec()))
            },
            label = "playIcon",
        ) { p ->
            Icon(
                if (p) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (p) "Пауза" else "Воспроизвести",
                Modifier.size(height * 0.46f),
            )
        }
    }
}

@Composable
private fun RepeatButton(mode: Int, onClick: () -> Unit) {
    val (icon, desc) = when (mode) {
        Player.REPEAT_MODE_ALL -> Icons.Rounded.RepeatOn to "Повтор: все"
        Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOneOn to "Повтор: один"
        else -> Icons.Rounded.Repeat to "Повтор выключен"
    }
    IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) {
        Icon(
            icon, desc,
            tint = if (mode == Player.REPEAT_MODE_OFF) LocalContentColor.current.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SpeedButton(speed: Float, onSelect: (Float) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, shapes = androidx.compose.material3.ButtonDefaults.shapes()) {
            Icon(Icons.Rounded.Speed, null, Modifier.size(18.dp), tint = if (speed != 1f) MaterialTheme.colorScheme.primary else LocalContentColor.current)
            Spacer(Modifier.width(6.dp))
            Text(speedLabel(speed), color = if (speed != 1f) MaterialTheme.colorScheme.primary else LocalContentColor.current)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(20.dp)) {
            Text(
                "Скорость воспроизведения",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Speeds.forEach { s ->
                DropdownMenuItem(
                    text = { Text(if (s == 1f) "Обычная (1×)" else speedLabel(s)) },
                    onClick = {
                        open = false
                        onSelect(s)
                    },
                    trailingIcon = if (s == speed) {
                        { Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary) }
                    } else null,
                )
            }
        }
    }
}

@Composable
private fun SleepButton(sleep: SleepTimer, compact: Boolean = false, onPoke: () -> Unit = {}) {
    var open by remember { mutableStateOf(false) }
    val active = sleep.mode != null
    val tint = if (active) MaterialTheme.colorScheme.primary else LocalContentColor.current
    Box {
        TextButton(onClick = { open = true; onPoke() }, shapes = androidx.compose.material3.ButtonDefaults.shapes()) {
            Icon(Icons.Rounded.Bedtime, "Таймер сна", Modifier.size(18.dp), tint = tint)
            if (active && !compact) {
                Spacer(Modifier.width(6.dp))
                Text(sleep.mode?.shortLabel.orEmpty(), color = tint)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(20.dp)) {
            Text(
                if (active) "Таймер сна: ${sleep.mode?.shortLabel.orEmpty()}" else "Таймер сна",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            SleepMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label) },
                    onClick = {
                        open = false
                        sleep.select(mode)
                        onPoke()
                    },
                    trailingIcon = if (sleep.mode == mode) {
                        { Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary) }
                    } else null,
                )
            }
            if (active) {
                DropdownMenuItem(
                    text = { Text("Отменить таймер") },
                    onClick = {
                        open = false
                        sleep.cancel()
                        onPoke()
                    },
                )
            }
        }
    }
}

@Composable
private fun MediaMoreMenu(current: FileItem, onBack: () -> Unit) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.MoreVert, "Ещё") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(20.dp)) {
            MediaMenuItem("Поделиться", Icons.Rounded.Share) {
                open = false
                FileOpener.share(context, listOf(File(current.path)))
            }
            MediaMenuItem("Открыть в другом приложении", Icons.AutoMirrored.Rounded.OpenInNew) {
                open = false
                FileOpener.openExternal(context, File(current.path))
            }
            MediaMenuItem("Показать в папке", Icons.Rounded.FolderOpen) {
                open = false
                context.appContainer.browserRequests.tryEmit(BrowserRequest.Reveal(current.path))
                onBack()
            }
        }
    }
}

@Composable
private fun MediaMenuItem(text: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text) }, onClick = onClick, leadingIcon = { Icon(icon, null) })
}

@Composable
private fun QueueSheet(playlist: List<FileItem>, current: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    val settings = LocalAppSettings.current
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Очередь", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text(
                "${current + 1} из ${playlist.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = (current - 2).coerceAtLeast(0))
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            itemsIndexed(playlist, key = { _, it -> it.path }) { i, item ->
                SegmentedListItem(
                    selected = i == current,
                    onClick = {
                        onSelect(i)
                        onDismiss()
                    },
                    shapes = ListItemDefaults.segmentedShapes(i, playlist.size),
                    leadingContent = { FileIcon(item, size = 40.dp) },
                    supportingContent = { Text(Format.size(item.size, settings.sizeUnits)) },
                    trailingContent = if (i == current) {
                        { Icon(Icons.Rounded.GraphicEq, "Играет", tint = MaterialTheme.colorScheme.primary) }
                    } else null,
                ) {
                    Text(item.baseName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Видео

private enum class VideoScale(val label: String, val scale: ContentScale) {
    Fit("Вписать", ContentScale.Fit),
    Crop("Заполнить", ContentScale.Crop),
}

@OptIn(ExperimentalLayoutApi::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoPlayerContent(
    player: ExoPlayer,
    ui: PlayerUi,
    actions: PlayerActions,
    playlist: List<FileItem>,
    current: FileItem,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
    sleep: SleepTimer,
) {
    val motion = MaterialTheme.motionScheme
    val activity = LocalActivity.current
    var controlsVisible by remember { mutableStateOf(true) }
    var poke by remember { mutableIntStateOf(0) }
    var scale by remember { mutableStateOf(VideoScale.Fit) }
    var seekHint by remember { mutableStateOf<Pair<Boolean, Int>?>(null) }
    var landscapeLock by remember { mutableStateOf(false) }

    ImmersiveSystemBars(hidden = !controlsVisible)
    KeepScreenOn(ui.isPlaying)

    // Блокировка ориентации по кнопке; исходная ориентация восстанавливается при уходе с экрана.
    val originalOrientation = remember(activity) { activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    DisposableEffect(activity) {
        onDispose { activity?.requestedOrientation = originalOrientation }
    }
    LaunchedEffect(landscapeLock) {
        activity?.requestedOrientation = if (landscapeLock) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else originalOrientation
    }

    // Автоскрытие панелей во время воспроизведения.
    LaunchedEffect(controlsVisible, ui.isPlaying, poke) {
        if (controlsVisible && ui.isPlaying) {
            delay(3500)
            controlsVisible = false
        }
    }
    LaunchedEffect(seekHint) {
        if (seekHint != null) {
            delay(650)
            seekHint = null
        }
    }
    val touch: () -> Unit = { poke++ }

    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            ContentFrame(
                player = player,
                modifier = Modifier.fillMaxSize(),
                contentScale = scale.scale,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(player) {
                        detectTapGestures(
                            onTap = { controlsVisible = !controlsVisible },
                            onDoubleTap = { offset ->
                                val forward = offset.x > size.width / 2
                                if (forward) actions.forward() else actions.back()
                                val prev = seekHint
                                val steps = if (prev != null && prev.first == forward) prev.second + 1 else 1
                                seekHint = forward to steps
                                poke++
                            },
                        )
                    },
            )
            if (ui.buffering) {
                LoadingIndicator(Modifier.align(Alignment.Center).size(72.dp), color = Color.White)
            }

            // Подсказка двойного тапа: «−10 с» / «+10 с».
            val hint = seekHint
            AnimatedVisibility(
                visible = hint != null,
                enter = scaleIn(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
                exit = scaleOut(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
                modifier = Modifier
                    .align(if (hint?.first == false) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 48.dp),
            ) {
                Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.55f), contentColor = Color.White) {
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (hint?.first == false) Icons.Rounded.Replay10 else Icons.Rounded.Forward10, null)
                        Spacer(Modifier.width(8.dp))
                        val secs = (hint?.second ?: 1) * (SEEK_STEP_MS / 1000)
                        Text(if (hint?.first == false) "−$secs с" else "+$secs с", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            AnimatedVisibility(
                visible = controlsVisible,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = slideInVertically(motion.defaultSpatialSpec()) { -it } + fadeIn(motion.defaultEffectsSpec()),
                exit = slideOutVertically(motion.fastSpatialSpec()) { -it } + fadeOut(motion.fastEffectsSpec()),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
                        .windowInsetsPadding(
                            WindowInsets.statusBarsIgnoringVisibility.union(WindowInsets.displayCutout)
                                .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                        )
                        .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад")
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        Text(current.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                        if (playlist.size > 1) {
                            Text(
                                "${ui.index + 1} из ${playlist.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.72f),
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            landscapeLock = !landscapeLock
                            touch()
                        },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            Icons.Rounded.ScreenRotation,
                            if (landscapeLock) "Вернуть ориентацию" else "Альбомная ориентация",
                            tint = if (landscapeLock) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                    MediaMoreMenu(current, onBack)
                }
            }

            AnimatedVisibility(
                visible = controlsVisible && ui.error == null,
                modifier = Modifier.align(Alignment.Center),
                enter = scaleIn(motion.defaultSpatialSpec(), initialScale = 0.8f) + fadeIn(motion.defaultEffectsSpec()),
                exit = scaleOut(motion.fastSpatialSpec(), targetScale = 0.8f) + fadeOut(motion.fastEffectsSpec()),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    val translucent = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.45f),
                        contentColor = Color.White,
                    )
                    if (playlist.size > 1) {
                        FilledTonalIconButton(
                            onClick = { actions.previous(); touch() },
                            shapes = IconButtonDefaults.shapes(),
                            colors = translucent,
                            modifier = Modifier.size(48.dp),
                        ) { Icon(Icons.Rounded.SkipPrevious, "Предыдущее") }
                    }
                    FilledTonalIconButton(
                        onClick = { actions.back(); touch() },
                        shapes = IconButtonDefaults.shapes(),
                        colors = translucent,
                        modifier = Modifier.size(60.dp),
                    ) { Icon(Icons.Rounded.Replay10, "Назад на 10 секунд", Modifier.size(30.dp)) }
                    PlayPauseButton(ui.showPause, onToggle = { actions.togglePlay(); touch() }, height = 80.dp)
                    FilledTonalIconButton(
                        onClick = { actions.forward(); touch() },
                        shapes = IconButtonDefaults.shapes(),
                        colors = translucent,
                        modifier = Modifier.size(60.dp),
                    ) { Icon(Icons.Rounded.Forward10, "Вперёд на 10 секунд", Modifier.size(30.dp)) }
                    if (playlist.size > 1) {
                        FilledTonalIconButton(
                            onClick = { actions.next(); touch() },
                            enabled = ui.hasNext,
                            shapes = IconButtonDefaults.shapes(),
                            colors = translucent,
                            modifier = Modifier.size(48.dp),
                        ) { Icon(Icons.Rounded.SkipNext, "Следующее") }
                    }
                }
            }

            AnimatedVisibility(
                visible = controlsVisible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(motion.defaultSpatialSpec()) { it } + fadeIn(motion.defaultEffectsSpec()),
                exit = slideOutVertically(motion.fastSpatialSpec()) { it } + fadeOut(motion.fastEffectsSpec()),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                        .windowInsetsPadding(
                            WindowInsets.navigationBarsIgnoringVisibility.union(WindowInsets.displayCutout)
                                .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                        )
                        .padding(start = 16.dp, end = 16.dp, top = 32.dp, bottom = 8.dp),
                ) {
                    SeekBar(ui, actions, wavy = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RepeatButton(ui.repeatMode) { actions.cycleRepeat(); touch() }
                        SpeedButton(ui.speed) { actions.setSpeed(it); touch() }
                        SleepButton(sleep, compact = true, onPoke = touch)
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = {
                                scale = if (scale == VideoScale.Fit) VideoScale.Crop else VideoScale.Fit
                                touch()
                            },
                            shapes = androidx.compose.material3.ButtonDefaults.shapes(),
                        ) {
                            Icon(if (scale == VideoScale.Fit) Icons.Rounded.FitScreen else Icons.Rounded.ZoomOutMap, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(scale.label, color = LocalContentColor.current)
                        }
                    }
                }
            }

            val error = ui.error
            if (error != null) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f))) {
                    ViewerMessage(
                        icon = Icons.Rounded.ErrorOutline,
                        title = "Не удалось воспроизвести",
                        message = errorText(error),
                        polygon = MaterialShapes.Clover4Leaf,
                        container = MaterialTheme.colorScheme.errorContainer,
                        content = MaterialTheme.colorScheme.onErrorContainer,
                    ) {
                        val context = LocalContext.current
                        ViewerTonalAction("Повторить", Icons.Rounded.Refresh, actions::retry)
                        ViewerTonalAction("Другое приложение", Icons.AutoMirrored.Rounded.OpenInNew) {
                            FileOpener.openExternal(context, File(current.path))
                        }
                    }
                }
            }
            SnackbarHost(
                snackbar,
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                    .padding(bottom = 88.dp),
            )
        }
    }
}
