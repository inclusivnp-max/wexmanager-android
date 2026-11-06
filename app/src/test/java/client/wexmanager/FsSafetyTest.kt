package client.wexmanager

import client.wexmanager.core.fs.LocalFileSystem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FsSafetyTest {
    @Test
    fun deleteTreeDoesNotFollowSymlinks() {
        val root = Files.createTempDirectory("wex-del").toFile()
        try {
            val precious = File(root, "precious").apply { mkdirs() }
            val keep = File(precious, "photo.jpg").apply { writeText("data") }
            val doomed = File(root, "doomed").apply { mkdirs() }
            File(doomed, "a/b").mkdirs()
            File(doomed, "a/b/file.txt").writeText("x")
            Files.createSymbolicLink(File(doomed, "link-to-precious").toPath(), precious.toPath())

            assertTrue(LocalFileSystem.deleteTree(doomed))
            assertFalse("удаляемая папка удалена", doomed.exists())
            assertTrue("цель ссылки не тронута", keep.isFile)
        } finally {
            root.deleteRecursively()
        }
    }
}
