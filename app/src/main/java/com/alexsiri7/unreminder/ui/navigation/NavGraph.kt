package com.alexsiri7.unreminder.ui.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.util.Log
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.drawToBitmap
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.alexsiri7.unreminder.ui.feedback.FeedbackScreen
import com.alexsiri7.unreminder.ui.habit.HabitEditScreen
import com.alexsiri7.unreminder.ui.habit.HabitListScreen
import com.alexsiri7.unreminder.ui.location.LocationScreen
import com.alexsiri7.unreminder.ui.recent.RecentTriggersScreen
import com.alexsiri7.unreminder.ui.settings.SettingsScreen
import com.alexsiri7.unreminder.ui.window.WindowEditScreen
import com.alexsiri7.unreminder.ui.window.WindowListScreen
import java.io.File

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Habits : Screen("habits", "Habits", Icons.Default.Repeat)
    data object Windows : Screen("windows", "Windows", Icons.Default.Timer)
    data object Recent : Screen("recent", "Recent", Icons.Default.History)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

val bottomNavItems = listOf(Screen.Habits, Screen.Windows, Screen.Recent, Screen.Settings)

private fun captureScreenshot(activity: Activity, onCaptured: (String) -> Unit) {
    try {
        val file = File(activity.cacheDir, "feedback-${System.currentTimeMillis()}.png")
        val bitmap = activity.window.decorView.drawToBitmap()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) }
        onCaptured(file.absolutePath)
    } catch (e: Exception) {
        Log.e("NavGraph", "drawToBitmap failed — cannot open feedback screen", e)
    }
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    val activity = LocalContext.current.findActivity() ?: return
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
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
            startDestination = Screen.Habits.route,
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
                LocationScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(Screen.Recent.route) {
                RecentTriggersScreen(
                    onSendFeedback = {
                        captureScreenshot(activity) { path ->
                            navController.navigate("feedback/${java.net.URLEncoder.encode(path, "UTF-8")}")
                        }
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToLocations = { navController.navigate("locations") },
                    onSendFeedback = {
                        captureScreenshot(activity) { path ->
                            navController.navigate("feedback/${java.net.URLEncoder.encode(path, "UTF-8")}")
                        }
                    }
                )
            }
            composable(
                "feedback/{screenshotPath}",
                arguments = listOf(navArgument("screenshotPath") { type = NavType.StringType })
            ) { backStackEntry ->
                val encoded = backStackEntry.arguments?.getString("screenshotPath") ?: ""
                val path = java.net.URLDecoder.decode(encoded, "UTF-8")
                FeedbackScreen(
                    screenshotPath = path,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
