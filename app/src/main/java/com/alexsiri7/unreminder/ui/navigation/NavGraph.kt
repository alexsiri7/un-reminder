package com.alexsiri7.unreminder.ui.navigation

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.alexsiri7.unreminder.data.repository.OnboardingRepository
import com.alexsiri7.unreminder.ui.habit.HabitEditScreen
import com.alexsiri7.unreminder.ui.habit.HabitListScreen
import com.alexsiri7.unreminder.ui.location.LocationScreen
import com.alexsiri7.unreminder.ui.location.MapPickerScreen
import com.alexsiri7.unreminder.ui.onboarding.OnboardingScreen
import com.alexsiri7.unreminder.ui.recent.RecentTriggersScreen
import com.alexsiri7.unreminder.ui.settings.SettingsScreen
import com.alexsiri7.unreminder.ui.window.WindowEditScreen
import com.alexsiri7.unreminder.ui.window.WindowListScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Habits : Screen("habits", "Habits", Icons.Default.Repeat)
    data object Windows : Screen("windows", "Windows", Icons.Default.Timer)
    data object Recent : Screen("recent", "Recent", Icons.Default.History)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

val bottomNavItems = listOf(Screen.Habits, Screen.Windows, Screen.Recent, Screen.Settings)

@HiltViewModel
class NavViewModel @Inject constructor(
    onboardingRepository: OnboardingRepository
) : ViewModel() {
    val isOnboarded: StateFlow<Boolean?> = onboardingRepository.isOnboardingCompleted
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun NavGraph(navViewModel: NavViewModel = hiltViewModel()) {
    val isOnboarded by navViewModel.isOnboarded.collectAsStateWithLifecycle()

    val startDestination = remember { mutableStateOf<String?>(null) }
    if (startDestination.value == null && isOnboarded != null) {
        startDestination.value = if (isOnboarded == true) Screen.Habits.route else "onboarding"
    }
    val resolvedStart = startDestination.value ?: return

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = currentDestination?.route != "onboarding"

    Scaffold(
        bottomBar = {
            if (showBottomBar) NavigationBar {
                bottomNavItems.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
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
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                "habit_edit/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { backStackEntry ->
                HabitEditScreen(
                    habitId = backStackEntry.arguments?.getLong("id"),
                    onNavigateBack = { navController.popBackStack() }
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
                RecentTriggersScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToLocations = { navController.navigate("locations") }
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
