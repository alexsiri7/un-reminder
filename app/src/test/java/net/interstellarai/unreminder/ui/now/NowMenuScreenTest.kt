package net.interstellarai.unreminder.ui.now

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import net.interstellarai.unreminder.domain.DisplayTier
import net.interstellarai.unreminder.domain.model.ActivityMode
import net.interstellarai.unreminder.service.notification.MascotSprites
import net.interstellarai.unreminder.ui.theme.UnReminderTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The activity row is the one place the Now page asks for a permission, and only while it is
 * denied: any other reading must render as plain text with nothing to tap. A menu row has two
 * taps that must stay apart: the row opens its variant, the chip completes.
 */
@RunWith(RobolectricTestRunner::class)
class NowMenuScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var permissionRequests = 0
    private val opened = mutableListOf<NowMenuItem>()
    private val completed = mutableListOf<Long>()

    private val item = NowMenuItem(
        habitId = 1,
        name = "meditation",
        text = "Two minutes of stillness before the next thing.",
        variationId = 11,
        spriteRes = MascotSprites.entries[0].drawableRes,
        tier = DisplayTier.DOABLE,
    )

    private fun show(activity: ActivityReading) = show(NowMenuUiState.NoHabits(allPaused = false), activity)

    private fun show(uiState: NowMenuUiState, activity: ActivityReading = ActivityReading.Observed(ActivityMode.SITTING)) {
        compose.setContent {
            UnReminderTheme {
                NowMenuContent(
                    uiState = uiState,
                    daysWithAnyCompletion = 0,
                    nowContext = NowContext(activity, LocationReading.Outside),
                    onRequestActivityPermission = { permissionRequests++ },
                    onComplete = { completed += it },
                    onLoadMore = {},
                    onAddHabit = {},
                    onNavigateToFeedback = {},
                    onOpen = { opened += it },
                )
            }
        }
    }

    @Test
    fun `tapping a row opens its variant and completes nothing`() {
        show(NowMenuUiState.Menu(items = listOf(item), canLoadMore = false))

        compose.onNodeWithText(item.text!!).performClick()

        assertEquals(listOf(item), opened)
        assertTrue(completed.isEmpty())
    }

    @Test
    fun `tapping did it completes in one tap without opening anything`() {
        show(NowMenuUiState.Menu(items = listOf(item), canLoadMore = false))

        compose.onNodeWithText("did it").performClick()

        assertEquals(listOf(item.habitId), completed)
        assertTrue(opened.isEmpty())
    }

    @Test
    fun `a denied permission is said as such and a tap asks for it`() {
        show(ActivityReading.PermissionDenied)

        compose.onNodeWithText("activity permission off · tap to allow")
            .assertHasClickAction()
            .performClick()

        assertEquals(1, permissionRequests)
    }

    @Test
    fun `cycling reads as the hold on nudges, with nothing to tap`() {
        show(ActivityReading.Cycling)

        compose.onNodeWithText("cycling · nudges held")
            .assertHasNoClickAction()
            .performClick()

        assertEquals(0, permissionRequests)
    }

    @Test
    fun `an observed mode is plain text with nothing to tap`() {
        show(ActivityReading.Observed(ActivityMode.WALKING))

        compose.onNodeWithText("walking")
            .assertHasNoClickAction()
            .performClick()

        assertEquals(0, permissionRequests)
    }

    @Test
    fun `an assumed mode is marked and has nothing to tap`() {
        show(ActivityReading.Assumed(ActivityMode.SITTING))

        compose.onNodeWithText("sitting (assumed)")
            .assertHasNoClickAction()
            .performClick()

        assertEquals(0, permissionRequests)
    }
}
