package client.wexmanager.core.apk

import org.junit.Assert.assertEquals
import org.junit.Test

class SplitMergerTest {
    @Test
    fun removesSplitMarkersFromManifest() {
        val xml = """
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="a.b" android:requiredSplitTypes="base__abi,base__density" android:splitTypes="">
    <application android:label="A" android:isSplitRequired="true">
        <meta-data android:name="com.android.vending.splits.required" android:value="true" />
        <meta-data android:name="keep.me" android:value="1" />
        <meta-data android:name="com.android.vending.splits" android:resource="@xml/splits0"></meta-data>
        <meta-data android:value="https://play.google.com/store" android:name="com.android.stamp.source" />
    </application>
</manifest>
""".trimIndent()
        val expected = """
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="a.b">
    <application android:label="A">
        <meta-data android:name="keep.me" android:value="1" />
    </application>
</manifest>
""".trimIndent()
        assertEquals(expected, SplitMerger.patchManifest(xml))
    }
}
