package client.wexmanager.core.fs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootListParserTest {
    @Test
    fun normalNamesKeepStatFields() {
        val data = rec(0x81a4, 4, 10, 0, 0, false, "a.txt") +
            rec(0x81a4, 8, 11, 1, 2, false, "файл.txt")
        val items = RootShell.parseList(data, "/data")
        assertEquals(listOf("a.txt", "файл.txt"), items.map { it.name })
        assertEquals("/data/a.txt", items[0].path)
        assertEquals(4L, items[0].size)
        assertEquals(10_000L, items[0].lastModified)
        assertEquals(0x81a4, items[0].mode)
        assertFalse(items[0].isDirectory)
        assertFalse(items[0].isSymlink)
        assertEquals(1, items[1].uid)
        assertEquals(2, items[1].gid)
    }

    @Test
    fun pipeAndNewlineDoNotSplitTheRecord() {
        val spoof = "a|b\n81a4|1|2|0|0|0|3\nbar"
        val data = rec(0x81a4, 1, 12, 0, 0, false, spoof) +
            rec(0x81a4, 2, 13, 0, 0, false, "ok")
        val items = RootShell.parseList(data, "/data")
        assertEquals(listOf(spoof, "ok"), items.map { it.name })
        assertEquals("/data/ok", items[1].path)
        assertTrue(items.none { it.name == "bar" })
    }

    @Test
    fun rejectsDotDotSlashAndEmpty() {
        val data = rec(0x81a4, 1, 1, 0, 0, false, "keep") +
            rec(0x41ed, 1, 1, 0, 0, true, "..") +
            rec(0x41ed, 1, 1, 0, 0, true, ".") +
            rec(0x81a4, 1, 1, 0, 0, false, "x/y") +
            rec(0x81a4, 1, 1, 0, 0, false, "") +
            rec(0x41ed, 40, 11, 1000, 1000, true, "sub")
        val items = RootShell.parseList(data, "/")
        assertEquals(listOf("keep", "sub"), items.map { it.name })
        assertEquals("/keep", items[0].path)
        assertEquals("/sub", items[1].path)
        assertTrue(items[1].isDirectory)
        assertFalse(items[1].isSymlink)
        assertEquals(0x41ed, items[1].mode)
    }

    @Test
    fun truncatedLengthDropsThePartialRecord() {
        val good = rec(0x81a4, 3, 9, 0, 0, false, "whole")
        val truncated = "81a4|3|9|0|0|0|8\nhi".toByteArray(Charsets.US_ASCII)
        val items = RootShell.parseList(good + truncated, "/data")
        assertEquals(listOf("whole"), items.map { it.name })
        assertTrue(RootShell.parseList(truncated, "/data").isEmpty())
        val huge = "81a4|1|2|0|0|0|99999999\nhi".toByteArray(Charsets.US_ASCII)
        assertTrue(RootShell.parseList(huge, "/data").isEmpty())
    }

    @Test
    fun symlinkDirectoryFollowsTheDirFlag() {
        val data = rec(0xa1ff, 99, 14, 0, 0, true, "linkdir") +
            rec(0xa1ff, 5, 15, 0, 0, false, "linkfile")
        val items = RootShell.parseList(data, "/data")
        assertTrue(items[0].isSymlink)
        assertTrue(items[0].isDirectory)
        assertEquals(0xa1ff, items[0].mode)
        assertEquals(99L, items[0].size)
        assertTrue(items[1].isSymlink)
        assertFalse(items[1].isDirectory)
    }

    private fun rec(mode: Int, size: Long, mtimeSec: Long, uid: Int, gid: Int, dir: Boolean, name: String): ByteArray {
        val bytes = name.toByteArray(Charsets.UTF_8)
        val header = "%x|%d|%d|%d|%d|%d|%d\n".format(mode, size, mtimeSec, uid, gid, if (dir) 1 else 0, bytes.size)
        return header.toByteArray(Charsets.US_ASCII) + bytes
    }
}
