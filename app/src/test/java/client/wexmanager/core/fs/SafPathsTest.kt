package client.wexmanager.core.fs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafPathsTest {
    private val root = "/storage/emulated/0"

    @Test
    fun documentIdUsesVolumeAndRelativePath() {
        assertEquals("primary:Android/data", SafPaths.documentId("primary", root, "$root/Android/data"))
        assertEquals(
            "primary:Android/data/org.example/files/a.txt",
            SafPaths.documentId("primary", root, "$root/Android/data/org.example/files/a.txt"),
        )
        assertEquals("primary:", SafPaths.documentId("primary", root, root))
    }

    @Test
    fun rejectsParentSegmentsAndOtherVolumes() {
        assertNull(SafPaths.documentId("primary", root, "$root/Android/data/../obb"))
        assertNull(SafPaths.documentId("primary", root, "/storage/AAAA-BBBB/Android/data"))
        assertNull(SafPaths.relative(root, "$root/Android/data/./x"))
    }

    @Test
    fun restrictedRootIsDataOrObb() {
        assertEquals("$root/Android/data", SafPaths.restrictedRoot(root, "$root/Android/data/org.example"))
        assertEquals("$root/Android/obb", SafPaths.restrictedRoot(root, "$root/Android/obb"))
        assertNull(SafPaths.restrictedRoot(root, "$root/Download"))
    }

    @Test
    fun grantCoversOnlyItsSubtree() {
        assertTrue(SafPaths.grantCovers(root, "Android/data", "$root/Android/data/org.example"))
        assertFalse(SafPaths.grantCovers(root, "Android/data", "$root/Android/obb"))
        assertTrue(SafPaths.grantCovers(root, "", "$root/Download/a"))
        assertFalse(SafPaths.grantCovers(root, "Android/data", "/data/data"))
    }

    @Test
    fun pickerUriOpensInsideTheRestrictedFolder() {
        val rewritten = SafAccess.rewriteInitial(
            "content://com.android.externalstorage.documents/root/primary",
            "primary",
            "Android%2Fdata",
        )
        assertEquals(
            "content://com.android.externalstorage.documents/document/primary%3AAndroid%2Fdata",
            rewritten,
        )
    }
}
