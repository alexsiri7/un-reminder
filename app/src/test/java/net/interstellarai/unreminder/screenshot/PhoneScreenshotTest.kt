package net.interstellarai.unreminder.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.LocationSettingsStatusCodes
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.domain.DisplayTier
import net.interstellarai.unreminder.service.geofence.GeofenceRegistration
import net.interstellarai.unreminder.service.geofence.LocationSettingsCheck
import net.interstellarai.unreminder.service.geofence.RegistrationHealth
import net.interstellarai.unreminder.service.llm.AiStatus
import net.interstellarai.unreminder.service.notification.MascotSprites
import net.interstellarai.unreminder.ui.habit.HabitListContent
import net.interstellarai.unreminder.ui.now.NowMenuContent
import net.interstellarai.unreminder.ui.now.NowMenuItem
import net.interstellarai.unreminder.ui.now.NowMenuUiState
import net.interstellarai.unreminder.ui.onboarding.OnboardingContent
import net.interstellarai.unreminder.ui.onboarding.OnboardingUiState
import net.interstellarai.unreminder.ui.settings.LocationTrackingSection
import net.interstellarai.unreminder.ui.settings.LocationTrackingStatus
import net.interstellarai.unreminder.ui.theme.Dimens
import net.interstellarai.unreminder.ui.theme.UnReminderTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.util.TimeZone

class PhoneScreenshotTest {

    // The section prints checkedAt as a clock time; the JVM zone must not vary by machine.
    @Before
    fun pinTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

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
                    habitAvailability = emptyMap(),
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

    @Test
    fun phone_4() {
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
    fun phone_5() {
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
}

internal val fakeHabits = listOf(
    HabitEntity(id = 1, name = "meditation", dedicationLevel = 3, active = true),
    HabitEntity(id = 2, name = "exercise", dedicationLevel = 2, active = true),
    HabitEntity(id = 3, name = "reading", dedicationLevel = 1, active = false),
)

internal val fakeNowMenu = NowMenuUiState.Menu(
    items = listOf(
        NowMenuItem(
            habitId = 1,
            name = "meditation",
            text = "Two minutes of stillness before the next thing — the astronaut kind, drifting.",
            variationId = 11,
            spriteRes = MascotSprites.entries[0].drawableRes,
            tier = DisplayTier.DOABLE,
        ),
        NowMenuItem(
            habitId = 2,
            name = "exercise",
            text = "A short walk around the block, treasure map optional.",
            variationId = 12,
            spriteRes = MascotSprites.entries[1].drawableRes,
            tier = DisplayTier.DOABLE,
        ),
        NowMenuItem(habitId = 3, name = "reading", text = null, variationId = null, spriteRes = MascotSprites.entries[2].drawableRes, tier = DisplayTier.DOABLE),
    ),
    canLoadMore = true,
)

internal val fakeRegistrationHealth = RegistrationHealth(
    outcomes = listOf(
        1L to GeofenceRegistration.Registered,
        2L to GeofenceRegistration.Rejected(GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE),
    ),
    fineLocationGranted = true,
    backgroundLocationGranted = true,
    locationEnabled = true,
    locationSettings = LocationSettingsCheck.Unavailable(LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE),
    checkedAt = Instant.parse("2026-09-11T09:41:00Z"),
)
