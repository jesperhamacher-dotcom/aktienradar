package de.hamacher.aktienradar

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import de.hamacher.aktienradar.data.Repo
import de.hamacher.aktienradar.ui.AppTheme
import de.hamacher.aktienradar.ui.DetailScreen
import de.hamacher.aktienradar.ui.RadarScreen
import de.hamacher.aktienradar.ui.SettingsScreen
import de.hamacher.aktienradar.ui.WatchlistScreen
import de.hamacher.aktienradar.work.CheckWorker

sealed interface Screen {
    data object Radar : Screen
    data object Watchlist : Screen
    data class Detail(val ticker: String) : Screen
    data object Settings : Screen
}

class MainActivity : ComponentActivity() {

    private val screen: MutableState<Screen> = mutableStateOf(Screen.Radar)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Repo.init(this)
        CheckWorker.schedule(this)
        handleIntent(intent)
        if (Repo.contactEmail.isBlank()) screen.value = Screen.Settings

        setContent {
            AppTheme {
                AskNotificationPermission()
                AppContent(screen)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val ticker = intent?.getStringExtra(EXTRA_TICKER) ?: return
        if (Repo.find(ticker) != null) screen.value = Screen.Detail(ticker)
    }

    companion object {
        const val EXTRA_TICKER = "ticker"
    }
}

@Composable
private fun AppContent(state: MutableState<Screen>) {
    var screen by state
    var lastTab by remember { mutableStateOf<Screen>(Screen.Radar) }
    if (screen == Screen.Radar || screen == Screen.Watchlist) lastTab = screen
    BackHandler(enabled = screen != Screen.Radar) {
        screen = if (screen == Screen.Watchlist) Screen.Radar else lastTab
    }
    val bottomBar: @Composable () -> Unit = {
        NavigationBar {
            NavigationBarItem(
                selected = screen == Screen.Radar,
                onClick = { screen = Screen.Radar },
                icon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text("Radar") },
            )
            NavigationBarItem(
                selected = screen == Screen.Watchlist,
                onClick = { screen = Screen.Watchlist },
                icon = { Icon(Icons.Default.Star, contentDescription = null) },
                label = { Text("Watchlist") },
            )
        }
    }
    when (val s = screen) {
        Screen.Radar -> RadarScreen(
            onOpen = { screen = Screen.Detail(it) },
            onSettings = { screen = Screen.Settings },
            bottomBar = bottomBar,
        )
        Screen.Watchlist -> WatchlistScreen(
            onOpen = { screen = Screen.Detail(it) },
            onSettings = { screen = Screen.Settings },
            bottomBar = bottomBar,
        )
        is Screen.Detail -> DetailScreen(ticker = s.ticker, onBack = { screen = lastTab })
        Screen.Settings -> SettingsScreen(onBack = { screen = lastTab })
    }
}

@Composable
private fun AskNotificationPermission() {
    if (Build.VERSION.SDK_INT < 33) return
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var asked by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!asked) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
