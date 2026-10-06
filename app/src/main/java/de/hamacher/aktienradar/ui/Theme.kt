package de.hamacher.aktienradar.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import de.hamacher.aktienradar.data.Level

private val Teal = Color(0xFF0F6B67)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFF5FD3B8))
        else -> lightColorScheme(primary = Teal)
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

fun levelColor(level: Level): Color = when (level) {
    Level.STRONG -> Color(0xFF2E9E5B)
    Level.WATCH -> Color(0xFFD9A21B)
    Level.CHECK -> Color(0xFFE07A1F)
    Level.WEAK -> Color(0xFFD2412F)
    Level.NODATA -> Color(0xFF8A8A8A)
}

val PositiveColor = Color(0xFF2E9E5B)
val NegativeColor = Color(0xFFD2412F)
