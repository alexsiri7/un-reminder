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
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import java.util.TimeZone

// Goldens are CI-authoritative: ci.yml records and commits them to the PR branch, never record
// or commit them locally. A local render differs from CI's by a whole-frame anti-aliasing shift
// (~4.5% on the tablets). See README, "Screenshot tests".
class PhoneScreenshotTest {

    // The header formats the date label with the default locale and the location section
    // prints checkedAt as a clock time in the default zone; neither may vary by machine.
    @Before
    fun pinLocaleAndTimeZone() {
        Locale.setDefault(Locale.US)
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
                    today = fakeToday,
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

    @Test
    fun phone_6() {
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

// One row per tier below DOABLE, in display order, so every reason line and the
// dimmed/outlined row treatment are pinned by a golden.
internal val fakeRankedLowerNowMenu = NowMenuUiState.Menu(
    items = listOf(
        NowMenuItem(
            habitId = 4,
            name = "stretching",
            text = "Reach for the ceiling, then the floor.",
            variationId = 14,
            spriteRes = MascotSprites.entries[3].drawableRes,
            tier = DisplayTier.RECENTLY_DISMISSED,
        ),
        NowMenuItem(habitId = 5, name = "journaling", text = null, variationId = null, spriteRes = MascotSprites.entries[4].drawableRes, tier = DisplayTier.PACED),
        NowMenuItem(habitId = 6, name = "piano", text = null, variationId = null, spriteRes = MascotSprites.entries[5].drawableRes, tier = DisplayTier.OUT_OF_HOURS),
        NowMenuItem(habitId = 7, name = "gym", text = null, variationId = null, spriteRes = MascotSprites.entries[6].drawableRes, tier = DisplayTier.ELSEWHERE),
        NowMenuItem(habitId = 8, name = "water the plants", text = null, variationId = null, spriteRes = MascotSprites.entries[7].drawableRes, tier = DisplayTier.DONE_TODAY),
    ),
    canLoadMore = false,
)

internal val fakeToday: LocalDate = LocalDate.of(2026, 9, 11)

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
