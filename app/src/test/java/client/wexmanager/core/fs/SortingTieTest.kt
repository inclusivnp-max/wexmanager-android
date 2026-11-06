package client.wexmanager.core.fs

import client.wexmanager.core.settings.SortBy
import org.junit.Assert.assertEquals
import org.junit.Test

class SortingTieTest {
    private fun item(
        path: String,
        name: String = path.substringAfterLast('/'),
        dir: Boolean = false,
        size: Long = 0,
        time: Long = 0,
    ) = FileItem(path, name, dir, size, time)

    @Test
    fun equalNamesBreakTiesByPath() {
        val items = listOf(item("/z/same.txt"), item("/a/same.txt"), item("/m/same.txt"))
        val asc = items.sortedWith(fileComparator(SortBy.Name, descending = false, foldersFirst = false)).map { it.path }
        assertEquals(listOf("/a/same.txt", "/m/same.txt", "/z/same.txt"), asc)
        val desc = items.sortedWith(fileComparator(SortBy.Name, descending = true, foldersFirst = false)).map { it.path }
        assertEquals(listOf("/z/same.txt", "/m/same.txt", "/a/same.txt"), desc)
    }

    @Test
    fun foldersStayFirstWhenNamesTie() {
        val items = listOf(
            item("/b/file.txt"),
            item("/c/dir", dir = true),
            item("/a/dir", dir = true),
        )
        val asc = items.sortedWith(fileComparator(SortBy.Name, descending = false, foldersFirst = true)).map { it.path }
        assertEquals(listOf("/a/dir", "/c/dir", "/b/file.txt"), asc)
        val desc = items.sortedWith(fileComparator(SortBy.Name, descending = true, foldersFirst = true)).map { it.path }
        assertEquals(listOf("/c/dir", "/a/dir", "/b/file.txt"), desc)
    }

    @Test
    fun dateSizeAndTypeTiesUsePathAfterName() {
        val items = listOf(
            item("/z/a.txt", time = 5, size = 1),
            item("/a/a.txt", time = 5, size = 1),
            item("/m/b.txt", time = 5, size = 1),
        )
        val expected = listOf("/a/a.txt", "/z/a.txt", "/m/b.txt")
        assertEquals(expected, items.sortedWith(fileComparator(SortBy.Date, false, false)).map { it.path })
        assertEquals(expected, items.sortedWith(fileComparator(SortBy.Size, false, false)).map { it.path })
        assertEquals(expected, items.sortedWith(fileComparator(SortBy.Type, false, false)).map { it.path })
    }
}
