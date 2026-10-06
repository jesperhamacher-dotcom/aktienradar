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
import de.hamacher.aktienradar.ui.SettingsScreen
import de.hamacher.aktienradar.ui.WatchlistScreen
import de.hamacher.aktienradar.work.CheckWorker

sealed interface Screen {
    data object Watchlist : Screen
    data class Detail(val ticker: String) : Screen
    data object Settings : Screen
}

class MainActivity : ComponentActivity() {

    private val screen: MutableState<Screen> = mutableStateOf(Screen.Watchlist)

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
    BackHandler(enabled = screen != Screen.Watchlist) { screen = Screen.Watchlist }
    when (val s = screen) {
        Screen.Watchlist -> WatchlistScreen(
            onOpen = { screen = Screen.Detail(it) },
            onSettings = { screen = Screen.Settings },
        )
        is Screen.Detail -> DetailScreen(ticker = s.ticker, onBack = { screen = Screen.Watchlist })
        Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Watchlist })
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
