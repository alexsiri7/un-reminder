package net.interstellarai.unreminder.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import net.interstellarai.unreminder.service.llm.AiStatus
import net.interstellarai.unreminder.ui.habit.HabitListContent
import net.interstellarai.unreminder.ui.location.LocationContent
import net.interstellarai.unreminder.ui.now.NowMenuContent
import net.interstellarai.unreminder.ui.onboarding.OnboardingContent
import net.interstellarai.unreminder.ui.onboarding.OnboardingUiState
import net.interstellarai.unreminder.ui.settings.LocationTrackingSection
import net.interstellarai.unreminder.ui.settings.LocationTrackingStatus
import net.interstellarai.unreminder.ui.theme.Dimens
import net.interstellarai.unreminder.ui.theme.UnReminderTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

// Goldens are CI-authoritative; see PhoneScreenshotTest.
class Tablet7ScreenshotTest {

    // The header formats the date label with the default locale and the location section
    // prints checkedAt as a clock time in the default zone; neither may vary by machine.
    @Before
    fun pinLocaleAndTimeZone() {
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_7.copy(
            screenWidth = 1080,
            screenHeight = 1920,
            xdpi = 320,
            ydpi = 320,
            density = Density.XHIGH,
        ),
    )

    @Test
    fun tablet7_1() {
        paparazzi.snapshot {
            UnReminderTheme {
                HabitListContent(
                    habits = fakeHabits,
                    aiStatus = AiStatus.Ready,
                    habitAvailability = emptyMap(),
                    today = fakeToday,
                    onAddHabit = {},
                    onEditHabit = {},
                    onNavigateToFeedback = {},
                )
            }
        }
    }

    @Test
    fun tablet7_2() {
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
    fun tablet7_3() {
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

    @Test
    fun tablet7_4() {
        paparazzi.snapshot {
            UnReminderTheme {
                NowMenuContent(
                    uiState = fakeNowMenu,
                    daysWithAnyCompletion = 12,
                    onComplete = {},
                    onLoadMore = {},
                    onAddHabit = {},
                    onNavigateToFeedback = {},
                )
            }
        }
    }

    @Test
    fun tablet7_5() {
        paparazzi.snapshot {
            UnReminderTheme {
                LocationTrackingSection(
                    health = fakeRegistrationHealth,
                    status = LocationTrackingStatus.of(fakeRegistrationHealth, backgroundRestricted = false),
                    onNavigateToLocations = {},
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(Dimens.xxl),
                )
            }
        }
    }

    @Test
    fun tablet7_6() {
        paparazzi.snapshot {
            UnReminderTheme {
                NowMenuContent(
                    uiState = fakeRankedLowerNowMenu,
                    daysWithAnyCompletion = 12,
                    onComplete = {},
                    onLoadMore = {},
                    onAddHabit = {},
                    onNavigateToFeedback = {},
                )
            }
        }
    }

    @Test
    fun tablet7_7() {
        paparazzi.snapshot {
            UnReminderTheme {
                LocationContent(
                    locations = fakeLocationRows,
                    recalculation = fakeRecalculationFailure,
                    onRecalculate = {},
                    onNavigateBack = {},
                    onAddLocation = {},
                    onEditLocation = {},
                )
            }
        }
    }
}
