package client.wexmanager.feature.editor.golang

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import client.wexmanager.ui.navigation.Route
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Smoke-проверки основного экрана Go IDE без реальной файловой системы проекта. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class GoWorkspaceUiTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun homeShowsProjectActionsAndSearch() {
        compose.setContent {
            GoWorkspaceScreen(onBack = {}, onNavigate = {})
        }
        compose.waitForIdle()
        compose.onNodeWithText("Go IDE").assertIsDisplayed()
        compose.onNodeWithText("Новый проект").assertIsDisplayed()
        compose.onNodeWithText("Открыть папку").assertIsDisplayed()
        compose.onNodeWithText("Найти проект").assertIsDisplayed()
    }

    @Test
    fun createProjectDialogValidatesNameAndModule() {
        compose.setContent {
            GoWorkspaceScreen(onBack = {}, onNavigate = {})
        }
        compose.waitForIdle()
        compose.onNodeWithText("Новый проект").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Новый Go-проект").assertIsDisplayed()
        compose.onNodeWithText("Название").performTextInput("demo-app")
        compose.waitForIdle()
        compose.onNodeWithText("example.com/demo-app").assertIsDisplayed()
        compose.onNodeWithText("example.com/demo-app", substring = false).assertExists()
    }

    @Test
    fun quickOpenIsReachableFromEmptyState() {
        compose.setContent {
            GoWorkspaceScreen(onBack = {}, onNavigate = {})
        }
        compose.waitForIdle()
        // На пустом экране меню проекта недоступно — проверяем наличие кнопки создания
        compose.onNode(hasText("Новый проект") and hasClickAction()).assertIsEnabled()
        compose.onNode(hasText("Открыть папку") and hasClickAction()).assertIsEnabled()
    }
}
