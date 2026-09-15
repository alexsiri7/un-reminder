package net.interstellarai.unreminder.ui.reminder

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import net.interstellarai.unreminder.service.notification.MascotSprites
import net.interstellarai.unreminder.ui.theme.UnReminderTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Whatever layout a target lands on, the screen must show the headline, the habit and a
 * working "Did it"; the layouts differ in where things sit, never in what is there.
 */
@RunWith(RobolectricTestRunner::class)
class ReminderDetailScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var completions = 0
    private val watched = mutableListOf<String>()

    private val promptText = "Two minutes of stillness before the next thing."

    private val state = ReminderDetailUiState(
        promptText = promptText,
        habitName = "meditation",
        dedicationLevel = 3,
        spriteRes = MascotSprites.entries[0].drawableRes,
        target = ReminderDetailTarget.Variant(1, 11),
        isLoading = false,
        canComplete = true,
    )

    private var shown by mutableStateOf<ReminderDetailUiState?>(null)

    // Content is set once per test; later calls swap the state so a test can walk every
    // layout under one composition.
    private fun show(uiState: ReminderDetailUiState) {
        val first = shown == null
        shown = uiState
        if (first) {
            compose.setContent {
                UnReminderTheme {
                    ReminderDetailContent(
                        uiState = shown!!,
                        onNavigateBack = {},
                        onComplete = { completions++ },
                        onWatch = { watched += it },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `every layout shows the headline, the habit label and a working Did it`() {
        for (layout in ReminderDetailLayout.entries) {
            show(state.copy(layout = layout))

            compose.onNodeWithText(promptText).assertIsDisplayed()
            compose.onNodeWithText("meditation").assertIsDisplayed()
            compose.onNodeWithText("Did it").performClick()
        }

        assertEquals(ReminderDetailLayout.entries.size, completions)
    }

    @Test
    fun `every layout holds up without a sprite`() {
        for (layout in ReminderDetailLayout.entries) {
            show(state.copy(layout = layout, spriteRes = null))

            compose.onNodeWithText(promptText).assertIsDisplayed()
            compose.onNodeWithText("Did it").performClick()
        }

        assertEquals(ReminderDetailLayout.entries.size, completions)
    }

    @Test
    fun `Watch appears only with a video and opens it`() {
        show(state)
        compose.onNodeWithText("Watch").assertDoesNotExist()

        val url = "https://www.youtube.com/results?search_query=box+breathing"
        show(state.copy(videoUrl = url))
        compose.onNodeWithText("Watch").performClick()

        assertEquals(listOf(url), watched)
    }

    @Test
    fun `Did it is withdrawn when the row cannot be completed`() {
        show(state.copy(canComplete = false))

        compose.onNodeWithText("Did it").assertDoesNotExist()
    }

    @Test
    fun `without variant text the habit name is the headline`() {
        show(state.copy(promptText = ""))

        compose.onAllNodesWithText("meditation").assertCountEquals(1)
    }
}
