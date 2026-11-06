package client.wexmanager.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import client.wexmanager.ui.navigation.Route
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorFileTabsTest {
    @get:Rule val compose = createComposeRule()
    private val files = listOf(
        EditorTabs.Tab(1, Route.Editor("/project/go.mod")),
        EditorTabs.Tab(2, Route.Editor("/project/main.go")),
    )

    @Test fun closeIsIndependentFromSelection() {
        val selected = mutableLongStateOf(2)
        var closed: Long? = null
        compose.setContent {
            MaterialExpressiveTheme {
                EditorFileTabs(files, selected.longValue, emptySet(), true,
                    onSelect = { selected.longValue = it }, onClose = { closed = it })
            }
        }
        compose.onNodeWithTag("file-tab-2").assertIsSelected()
        compose.onNodeWithTag("file-tab-close-1").performClick()
        compose.runOnIdle { assertEquals(1L, closed); assertEquals(2L, selected.longValue) }
        compose.onNodeWithTag("file-tab-1").performClick()
        compose.onNodeWithTag("file-tab-1").assertIsSelected()
        compose.onNodeWithTag("file-tab-2").assertIsNotSelected()
    }

    @Test fun dirtyStateAndDisabledCloseAreAccessible() {
        compose.setContent {
            MaterialExpressiveTheme {
                EditorFileTabs(files, 1, setOf("/project/main.go"), false, onSelect = {}, onClose = {})
            }
        }
        compose.onNodeWithTag("file-tab-2").assertContentDescriptionEquals("main.go, есть несохранённые изменения")
        compose.onNodeWithTag("file-tab-close-2").assertIsNotEnabled()
        compose.onNodeWithTag("file-tab-2").assertHasClickAction()
    }

    @Test fun railDoesNotPaintAPlateInDarkTheme() = assertTransparentRail(dark = true)
    @Test fun railDoesNotPaintAPlateInLightTheme() = assertTransparentRail(dark = false)

    private fun assertTransparentRail(dark: Boolean) {
        val parent = Color(0xFF28394A)
        compose.setContent {
            MaterialExpressiveTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Box(Modifier.width(320.dp).background(parent)) {
                    EditorFileTabs(files, 1, emptySet(), true, onSelect = {}, onClose = {})
                }
            }
        }
        val pixels = compose.onNodeWithTag("editor-file-tabs").captureToImage().toPixelMap()
        assertEquals(parent.toArgb(), pixels[0, pixels.height / 2].toArgb())
        assertEquals(parent.toArgb(), pixels[pixels.width / 2, 0].toArgb())
    }

    @Test fun activeFileScrollsIntoView() {
        val many = (1L..20L).map { EditorTabs.Tab(it, Route.Editor("/project/file$it.go")) }
        compose.setContent {
            MaterialExpressiveTheme {
                Box(Modifier.width(320.dp)) {
                    EditorFileTabs(many, 20, emptySet(), true, onSelect = {}, onClose = {})
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("file-tab-20").assertIsDisplayed().assertIsSelected()
    }
}
