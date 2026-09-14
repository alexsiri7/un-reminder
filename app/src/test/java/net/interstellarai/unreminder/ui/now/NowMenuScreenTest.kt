package net.interstellarai.unreminder.ui.now

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import net.interstellarai.unreminder.domain.model.ActivityMode
import net.interstellarai.unreminder.ui.theme.UnReminderTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The activity row is the one place the Now page asks for a permission, and only while it is
 * denied: any other reading must render as plain text with nothing to tap.
 */
@RunWith(RobolectricTestRunner::class)
class NowMenuScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var permissionRequests = 0

    private fun show(activity: ActivityReading) {
        compose.setContent {
            UnReminderTheme {
                NowMenuContent(
                    uiState = NowMenuUiState.NoHabits(allPaused = false),
                    daysWithAnyCompletion = 0,
                    nowContext = NowContext(activity, LocationReading.Outside),
                    onRequestActivityPermission = { permissionRequests++ },
                    onComplete = {},
                    onLoadMore = {},
                    onAddHabit = {},
                    onNavigateToFeedback = {},
                )
            }
        }
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
