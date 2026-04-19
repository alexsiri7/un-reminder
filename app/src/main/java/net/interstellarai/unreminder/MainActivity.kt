package net.interstellarai.unreminder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import net.interstellarai.unreminder.ui.navigation.NavGraph
import net.interstellarai.unreminder.ui.theme.UnReminderTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UnReminderTheme {
                NavGraph()
            }
        }
    }
}
