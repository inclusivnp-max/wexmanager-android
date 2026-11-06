package client.wexmanager

import client.wexmanager.core.fs.FileItem
import client.wexmanager.core.fs.FileKind
import client.wexmanager.core.fs.Location
import client.wexmanager.core.fs.NameFilter
import client.wexmanager.core.fs.NaturalOrder
import client.wexmanager.core.fs.Permissions
import client.wexmanager.core.fs.fileComparator
import client.wexmanager.core.fs.isAncestorOf
import client.wexmanager.core.settings.SortBy
import client.wexmanager.feature.browser.BatchRename
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreLogicTest {

    private fun file(name: String, dir: Boolean = false, size: Long = 0, time: Long = 0) =
        FileItem("/x/$name", name, dir, size, time)

    @Test
    fun naturalOrderSortsNumbersByValue() {
        val sorted = listOf("file10.txt", "file2.txt", "File1.txt", "file02.txt", "a", "B").sortedWith(NaturalOrder)
        assertEquals(listOf("a", "B", "File1.txt", "file2.txt", "file02.txt", "file10.txt"), sorted)
    }

    @Test
    fun foldersFirstAndDescending() {
        val items = listOf(file("b.txt", size = 5), file("Zdir", dir = true), file("a.txt", size = 50), file("Adir", dir = true))
        val byName = items.sortedWith(fileComparator(SortBy.Name, descending = false, foldersFirst = true)).map { it.name }
        assertEquals(listOf("Adir", "Zdir", "a.txt", "b.txt"), byName)
        val bySizeDesc = items.sortedWith(fileComparator(SortBy.Size, descending = true, foldersFirst = true)).map { it.name }
        assertEquals(setOf("Adir", "Zdir"), bySizeDesc.take(2).toSet())
        assertEquals(listOf("a.txt", "b.txt"), bySizeDesc.drop(2))
    }

    @Test
    fun nameFilterModes() {
        assertTrue(NameFilter.parse("doc").matches("MyDocument.pdf"))
        assertFalse(NameFilter.parse("!doc").matches("MyDocument.pdf"))
        assertTrue(NameFilter.parse("*.apk").matches("App.APK"))
        assertFalse(NameFilter.parse("*.apk").matches("app.apks"))
        assertTrue(NameFilter.parse("img_??.png").matches("IMG_01.png"))
        assertTrue(NameFilter.parse("/^\\d{4}-").matches("2026-10-03 log.txt"))
        assertTrue(NameFilter.parse("!/\\.tmp$").matches("keep.txt"))
        assertFalse(NameFilter.parse("!/\\.tmp$").matches("junk.tmp"))
        assertFalse(NameFilter.parse("/[unclosed").isValid)
    }

    @Test
    fun batchRenamePattern() {
        val items = listOf(file("photo.jpg"), file("scan.png"))
        val plan = BatchRename.plan(items, "img_{n:3}", "", "", false, 1)
        assertEquals(listOf("img_001.jpg", "img_002.png"), plan.map { it.second })
        val replaced = BatchRename.plan(items, "{name}", "o", "0", false, 1)
        assertEquals(listOf("ph0t0.jpg", "scan.png"), replaced.map { it.second })
        val regex = BatchRename.plan(items, "{name}.{ext}", "^(\\w)", "X_$1", true, 1)
        assertEquals(listOf("X_photo.jpg", "X_scan.png"), regex.map { it.second })
    }

    @Test
    fun permissionsFormatting() {
        assertEquals("-rwxr-xr--", Permissions.toString(0b111_101_100, isDirectory = false, isSymlink = false))
        assertEquals("drwxrws--T", Permissions.toString(0x400 or 0x200 or 0b111_111_000, isDirectory = true, isSymlink = false))
        assertEquals("0755", Permissions.toOctal(0b111_101_101))
    }

    @Test
    fun locationHierarchy() {
        val root = Location.Local("/storage/emulated/0")
        val deep = Location.Local("/storage/emulated/0/Download/a")
        assertTrue(root.isAncestorOf(deep))
        assertFalse(deep.isAncestorOf(root))
        val archiveInner = Location.Archive("/storage/emulated/0/Download/x.zip", "res/drawable")
        assertEquals(Location.Archive("/storage/emulated/0/Download/x.zip", "res"), archiveInner.parent)
        assertEquals(Location.Local("/storage/emulated/0/Download"), Location.Archive("/storage/emulated/0/Download/x.zip").parent)
        assertTrue(Location.Local("/storage/emulated/0/Download").isAncestorOf(archiveInner))
    }

    @Test
    fun fileKindDetection() {
        assertEquals(FileKind.Apk, FileKind.of("app.apk", false))
        assertEquals(FileKind.Code, FileKind.of("Main.kt", false))
        assertEquals(FileKind.Archive, FileKind.of("data.tar.gz", false))
        assertEquals(FileKind.Code, FileKind.of("Makefile", false))
        assertEquals(FileKind.Folder, FileKind.of("anything.zip", true))
        assertEquals(FileKind.Unknown, FileKind.of("noext", false))
    }
}
