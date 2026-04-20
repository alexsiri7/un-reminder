package net.interstellarai.unreminder.ui.navigation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import net.interstellarai.unreminder.ui.habit.HabitEditScreen
import net.interstellarai.unreminder.ui.habit.HabitListScreen
import net.interstellarai.unreminder.ui.location.LocationScreen
import net.interstellarai.unreminder.ui.location.MapPickerScreen
import net.interstellarai.unreminder.ui.onboarding.OnboardingScreen
import net.interstellarai.unreminder.ui.feedback.FeedbackScreen
import net.interstellarai.unreminder.ui.recent.RecentTriggersScreen
import net.interstellarai.unreminder.ui.settings.SettingsScreen
import net.interstellarai.unreminder.ui.window.WindowEditScreen
import net.interstellarai.unreminder.ui.window.WindowListScreen

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Habits : Screen("habits", "Habits", Icons.Default.Repeat)
    data object Windows : Screen("windows", "Windows", Icons.Default.Timer)
    data object Recent : Screen("recent", "Recent", Icons.Default.History)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

val bottomNavItems = listOf(Screen.Habits, Screen.Windows, Screen.Recent, Screen.Settings)

@Composable
fun NavGraph(navViewModel: NavViewModel = hiltViewModel()) {
    val isOnboarded by navViewModel.isOnboarded.collectAsStateWithLifecycle()

    // Lock startDestination once: prevents NavHost from re-routing after onboarding
    // completes and isOnboarded flips from false → true during the same session.
    val startDestination = remember { mutableStateOf<String?>(null) }
    if (startDestination.value == null && isOnboarded != null) {
        startDestination.value = if (isOnboarded == true) Screen.Habits.route else "onboarding"
    }
    val resolvedStart = startDestination.value ?: return

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    var feedbackScreenshot by remember { mutableStateOf<Bitmap?>(null) }
    val activity = LocalContext.current as? android.app.Activity
    // Screenshot must be captured before navigating so we get the current screen,
    // not the FeedbackScreen overlay. Clear any stale screenshot first so a failed
    // capture never shows a bitmap from a previous session.
    fun captureAndNavigate(destination: String) {
        val view = activity?.window?.decorView ?: return
        feedbackScreenshot = null  // clear stale state before capture
        try {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            feedbackScreenshot = bitmap
        } catch (_: Exception) {
            // capture failed, navigate with null screenshot
        }
        navController.navigate(destination)
    }

    val showBottomBar = currentDestination?.route != "onboarding"

    Scaffold(
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) NavigationBar(
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp,
            ) {
                bottomNavItems.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = {
                            Text(
                                screen.label.lowercase(),
                                style = net.interstellarai.unreminder.ui.theme.MonoLabel,
                            )
                        },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                            selectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            selectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            unselectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onBackground
                                .copy(alpha = 0.6f),
                            unselectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onBackground
                                .copy(alpha = 0.6f),
                            indicatorColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = resolvedStart,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Habits.route) {
                HabitListScreen(
                    onAddHabit = { navController.navigate("habit_add") },
                    onEditHabit = { id -> navController.navigate("habit_edit/$id") }
                )
            }
            composable("habit_add") {
                HabitEditScreen(
                    habitId = null,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(
                "habit_edit/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { backStackEntry ->
                HabitEditScreen(
                    habitId = backStackEntry.arguments?.getLong("id"),
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Screen.Windows.route) {
                WindowListScreen(
                    onAddWindow = { navController.navigate("window_add") },
                    onEditWindow = { id -> navController.navigate("window_edit/$id") }
                )
            }
            composable("window_add") {
                WindowEditScreen(
                    windowId = null,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                "window_edit/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { backStackEntry ->
                WindowEditScreen(
                    windowId = backStackEntry.arguments?.getLong("id"),
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("locations") {
                LocationScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onAddLocation = { navController.navigate("location_add") },
                    onEditLocation = { label -> navController.navigate("location_edit/${Uri.encode(label)}") }
                )
            }
            composable("location_add") {
                MapPickerScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                "location_edit/{label}",
                arguments = listOf(navArgument("label") { type = NavType.StringType })
            ) { backStackEntry ->
                MapPickerScreen(
                    existingLabel = backStackEntry.arguments?.getString("label"),
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Recent.route) {
                RecentTriggersScreen(
                    onNavigateToFeedback = { captureAndNavigate("feedback") }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToLocations = { navController.navigate("locations") },
                    onNavigateToFeedback = { captureAndNavigate("feedback") }
                )
            }
            composable("feedback") {
                FeedbackScreen(
                    screenshotBitmap = feedbackScreenshot,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("onboarding") {
                OnboardingScreen(
                    onFinished = {
                        navController.navigate(Screen.Habits.route) {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
