package net.interstellarai.unreminder.screenshot

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.service.llm.AiStatus
import net.interstellarai.unreminder.ui.habit.HabitListContent
import net.interstellarai.unreminder.ui.onboarding.OnboardingContent
import net.interstellarai.unreminder.ui.onboarding.OnboardingUiState
import net.interstellarai.unreminder.ui.theme.UnReminderTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalTime

class PhoneScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 1080,
            screenHeight = 1920,
            xdpi = 480,
            ydpi = 480,
            density = Density.XXHIGH,
        ),
    )

    @Test
    fun phone_1() {
        paparazzi.snapshot {
            UnReminderTheme {
                HabitListContent(
                    habits = fakeHabits,
                    aiStatus = AiStatus.Ready,
                    onAddHabit = {},
                    onEditHabit = {},
                    onNavigateToFeedback = {},
                )
            }
        }
    }

    @Test
    fun phone_2() {
        paparazzi.snapshot {
            UnReminderTheme {
                OnboardingContent(
                    uiState = OnboardingUiState(step = 0),
                    onSkip = {},
                    onGrantNotification = {},
                    onGrantLocation = {},
                    onAdvanceToStep = {},
                    onUpdateHabitName = {},
                    onPickWindowStart = {},
                    onPickWindowEnd = {},
                    onComplete = {},
                )
            }
        }
    }

    @Test
    fun phone_3() {
        paparazzi.snapshot {
            UnReminderTheme {
                OnboardingContent(
                    uiState = OnboardingUiState(
                        step = 1,
                        hasNotificationPermission = true,
                        hasFineLocationPermission = true,
                    ),
                    onSkip = {},
                    onGrantNotification = {},
                    onGrantLocation = {},
                    onAdvanceToStep = {},
                    onUpdateHabitName = {},
                    onPickWindowStart = {},
                    onPickWindowEnd = {},
                    onComplete = {},
                )
            }
        }
    }
}

internal val fakeHabits = listOf(
    HabitEntity(id = 1, name = "meditation", dedicationLevel = 3, active = true),
    HabitEntity(id = 2, name = "exercise", dedicationLevel = 2, active = true),
    HabitEntity(id = 3, name = "reading", dedicationLevel = 1, active = false),
)
