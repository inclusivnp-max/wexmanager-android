package client.wexmanager.core.util

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import client.wexmanager.R
import client.wexmanager.core.settings.Bookmark
import java.io.File

/** Ярлыки на иконке приложения (долгое нажатие в лаунчере): «Загрузки» и закладки-папки. */
object AppShortcuts {
    private var last: List<String>? = null

    fun update(context: Context, bookmarks: List<Bookmark>) {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path
        val max = runCatching { ShortcutManagerCompat.getMaxShortcutCountPerActivity(context) }.getOrDefault(4).coerceIn(1, 4)
        val entries = buildList {
            add(Triple(downloads, "Загрузки", R.drawable.ic_shortcut_download))
            bookmarks.filter { it.isDirectory }.forEach { add(Triple(it.path, it.title.ifBlank { File(it.path).name }, R.drawable.ic_shortcut_folder)) }
        }.distinctBy { it.first }.take(max)
        val key = entries.map { "${it.first}|${it.second}" }
        if (key == last) return // лаунчер ограничивает частоту обновлений — не дёргаем без изменений
        val shortcuts = entries.mapIndexed { rank, (path, title, icon) ->
            ShortcutInfoCompat.Builder(context, "dyn:$path")
                .setShortLabel(title.take(24).ifEmpty { "/" })
                .setLongLabel(path)
                .setIcon(IconCompat.createWithResource(context, icon))
                .setRank(rank)
                .setIntent(
                    Intent(IncomingIntents.ACTION_OPEN_PATH)
                        .putExtra(IncomingIntents.EXTRA_PATH, path)
                        .setClassName(context, "client.wexmanager.MainActivity"),
                )
                .build()
        }
        if (runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }.getOrDefault(false)) last = key
    }
}
