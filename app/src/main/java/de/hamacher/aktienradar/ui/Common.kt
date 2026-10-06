package de.hamacher.aktienradar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.hamacher.aktienradar.data.Analysis
import de.hamacher.aktienradar.data.Level

@Composable
fun ScoreBadge(analysis: Analysis, size: Dp = 52.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(levelColor(analysis.level), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (analysis.level == Level.NODATA) "–" else analysis.score.toString(),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = if (size > 60.dp) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
        )
    }
}

const val DISCLAIMER =
    "Aktienradar wertet öffentlich gemeldete Zahlen aus und liefert Hinweise zur eigenen Prüfung – keine Anlageberatung und keine Kauf- oder Verkaufsempfehlung. " +
        "Der Score ist eine einfache Heuristik, die nicht durch Backtests belegt ist. Daten: SEC EDGAR (nur bei der SEC meldepflichtige Unternehmen)."
