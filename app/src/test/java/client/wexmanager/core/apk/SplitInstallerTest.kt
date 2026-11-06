package client.wexmanager.core.apk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SplitInstallerTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun qualifiers() {
        assertEquals("arm64_v8a", SplitInstaller.qualifierOf("config.arm64_v8a"))
        assertEquals("ru", SplitInstaller.qualifierOf("split_config.ru"))
        assertEquals("xxhdpi", SplitInstaller.qualifierOf("base-xxhdpi"))
        assertEquals("en", SplitInstaller.qualifierOf("feature-en"))
        assertNull(SplitInstaller.qualifierOf("base-master"))
        assertNull(SplitInstaller.qualifierOf("base"))
        assertNull(SplitInstaller.qualifierOf("feature-master"))
        assertNull(SplitInstaller.qualifierOf("com.example.app"))
    }

    private fun bundle(vararg names: Pair<String, Int>): File {
        val f = tmp.newFile("t.apks")
        ZipOutputStream(f.outputStream()).use { z -> names.forEach { (n, size) -> z.putNextEntry(ZipEntry(n)); z.write(ByteArray(size)); z.closeEntry() } }
        return f
    }

    @Test
    fun picksDeviceSplits() {
        val f = bundle(
            "splits/base-master.apk" to 900, "splits/base-arm64_v8a.apk" to 300, "splits/base-armeabi_v7a.apk" to 250,
            "splits/base-x86_64.apk" to 310, "splits/base-mdpi.apk" to 10, "splits/base-xxhdpi.apk" to 20,
            "splits/base-xxxhdpi.apk" to 30, "splits/base-ru.apk" to 5, "splits/base-de.apk" to 5, "splits/camera-master.apk" to 50,
        )
        val b = SplitInstaller.read(f, abis = listOf("arm64-v8a", "armeabi-v7a"), densityDpi = 440, languages = listOf("ru", "en"))
        assertEquals("splits/base-master.apk", b.base?.entry)
        val picked = b.splits.filter { it.recommended }.map { it.entry.substringAfter('/') }.toSet()
        assertEquals(setOf("base-master.apk", "base-arm64_v8a.apk", "base-xxhdpi.apk", "base-ru.apk", "camera-master.apk"), picked)
        assertEquals(SplitGroup.Feature, b.splits.first { it.entry.endsWith("camera-master.apk") }.group)
    }

    @Test
    fun xapkBaseByLargestWithoutQualifier() {
        val f = bundle("com.example.apk" to 1000, "config.arm64_v8a.apk" to 2000, "config.hdpi.apk" to 10)
        val b = SplitInstaller.read(f, abis = listOf("x86_64"), densityDpi = 160, languages = listOf("en"))
        assertEquals("com.example.apk", b.base?.entry)
        assertEquals(setOf("com.example.apk", "config.hdpi.apk"), b.splits.filter { it.recommended }.map { it.entry }.toSet())
    }
}
