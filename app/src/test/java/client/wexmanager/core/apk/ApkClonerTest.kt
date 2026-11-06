package client.wexmanager.core.apk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkClonerTest {
    @Test
    fun renamesPackageAndKeepsComponentClasses() {
        val xml = """
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.a.app" android:sharedUserId="com.a.shared">
    <permission android:name="com.a.app.permission.C2D" android:protectionLevel="signature" />
    <permission android:name="custom.PERM" />
    <uses-permission android:name="com.a.app.permission.C2D" />
    <uses-permission android:name="android.permission.INTERNET" />
    <application android:name=".App" android:label="A">
        <activity android:name=".Main" />
        <activity android:name="Second" />
        <activity-alias android:name="com.a.app.Alias" android:targetActivity=".Main" />
        <service android:name="org.lib.Service" android:permission="custom.PERM" />
        <provider android:name="androidx.core.content.FileProvider" android:authorities="com.a.app.files;other.auth" />
        <meta-data android:name="key" android:value=".NotAClass" />
    </application>
</manifest>
""".trimIndent()
        val out = ApkCloner.patchManifest(xml, "com.a.clone")
        assertTrue(out, out.contains("package=\"com.a.clone\""))
        assertTrue(out.contains("android:name=\"com.a.app.App\""))
        assertTrue(out.contains("<activity android:name=\"com.a.app.Main\""))
        assertTrue(out.contains("android:name=\"com.a.app.Second\""))
        assertTrue(out.contains("android:targetActivity=\"com.a.app.Main\""))
        assertTrue(out.contains("android:name=\"org.lib.Service\""))
        assertTrue(out.contains("android:authorities=\"com.a.clone.files;com.a.clone.other.auth\""))
        assertEquals(2, Regex("com\\.a\\.clone\\.permission\\.C2D").findAll(out).count())
        assertTrue(out.contains("android:permission=\"com.a.clone.custom.PERM\""))
        assertTrue(out.contains("android.permission.INTERNET"))
        assertTrue(out.contains("android:value=\".NotAClass\""))
        assertFalse(out.contains("sharedUserId"))
    }

    @Test
    fun validatesPackageNames() {
        assertTrue(ApkCloner.isValidPackage("com.example.app2"))
        assertFalse(ApkCloner.isValidPackage("single"))
        assertFalse(ApkCloner.isValidPackage("com.1bad"))
        assertFalse(ApkCloner.isValidPackage("com..x"))
    }
}
