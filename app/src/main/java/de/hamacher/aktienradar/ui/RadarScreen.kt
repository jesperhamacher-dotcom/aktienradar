package de.hamacher.aktienradar.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.hamacher.aktienradar.data.Candidate
import de.hamacher.aktienradar.data.Repo
import de.hamacher.aktienradar.data.money
import de.hamacher.aktienradar.data.pct
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private val SIZE_FILTERS = listOf(
    "alle" to 0.0,
    "≥ 100 Mio. $" to 100e6,
    "≥ 1 Mrd. $" to 1e9,
    "≥ 5 Mrd. $" to 5e9,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarScreen(
    onOpen: (String) -> Unit,
    onSettings: () -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scan by Repo.scan.collectAsState()
    val status by Repo.scanStatus.collectAsState()
    val watch by Repo.items.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var sizeIdx by remember { mutableIntStateOf(1) }
    var onlyProfitable by remember { mutableStateOf(false) }
    var onlyAccelerating by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf<String?>(null) }

    fun startScan() {
        scope.launch {
            try {
                Repo.runScan(context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                snackbar.showSnackbar(e.message ?: e.toString())
            }
        }
    }

    // Beim ersten Öffnen automatisch scannen
    LaunchedEffect(Unit) {
        if (Repo.scan.value == null && Repo.contactEmail.contains("@")) startScan()
    }

    val minRevenue = SIZE_FILTERS[sizeIdx].second
    val shown = scan?.candidates.orEmpty()
        .filter { it.revenue >= minRevenue }
        .filter { !onlyProfitable || (it.opMarginNow ?: -1.0) > 0 }
        .filter { !onlyAccelerating || (it.growthBefore != null && it.growthNow - it.growthBefore >= 0.03) }
        .take(100)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Radar") },
                actions = {
                    IconButton(enabled = status == null, onClick = { startScan() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Markt neu scannen")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Einstellungen")
                    }
                },
            )
        },
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    status?.let {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                    val s = scan
                    if (s == null && status == null) {
                        Text("Potenzielle Aktien finden", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Der Radar prüft alle bei der US-Börsenaufsicht meldenden Unternehmen (mehrere tausend) " +
                                "und zeigt die, bei denen Umsatzwachstum und Margen sich gerade verbessern. Dauert ca. 1 Minute.",
                        )
                        if (!Repo.contactEmail.contains("@")) {
                            Text("Bitte zuerst in den Einstellungen deine E-Mail eintragen.", color = NegativeColor)
                            TextButton(onClick = onSettings) { Text("Zu den Einstellungen") }
                        } else {
                            Button(onClick = { startScan() }) { Text("Markt jetzt scannen") }
                        }
                    }
                    if (s != null) {
                        val time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(s.timestamp))
                        Text(
                            "${s.period.label} · ${s.companiesChecked} Unternehmen geprüft · ${shown.size} Treffer · Stand $time",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SIZE_FILTERS.forEachIndexed { i, (label, _) ->
                                FilterChip(selected = sizeIdx == i, onClick = { sizeIdx = i }, label = { Text(label) })
                            }
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = onlyAccelerating, onClick = { onlyAccelerating = !onlyAccelerating }, label = { Text("Wachstum beschleunigt") })
                            FilterChip(selected = onlyProfitable, onClick = { onlyProfitable = !onlyProfitable }, label = { Text("Nur profitabel") })
                        }
                        Text(
                            "Umsatzgröße = Quartalsumsatz. Antippen öffnet die Detailanalyse und legt die Aktie in deine Watchlist.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            items(shown, key = { it.cik }) { c ->
                val inWatch = watch.any { it.ticker == c.ticker }
                CandidateCard(c, inWatch = inWatch, loading = adding == c.ticker) {
                    if (inWatch) {
                        onOpen(c.ticker)
                    } else if (adding == null) {
                        scope.launch {
                            adding = c.ticker
                            val error = Repo.add(context, c.ticker)
                            adding = null
                            if (Repo.find(c.ticker) != null) onOpen(c.ticker)
                            else if (error != null) snackbar.showSnackbar(error)
                        }
                    }
                }
            }
            item {
                Text(DISCLAIMER, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun CandidateCard(c: Candidate, inWatch: Boolean, loading: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).background(levelColor(c.level), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(c.score.toString(), color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.ticker, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(c.name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
                Text(
                    when {
                        loading -> "lädt …"
                        inWatch -> "✓ Watchlist"
                        else -> "+ Watchlist"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(4.dp))
            val growthText = buildString {
                append("Umsatz ${pct(c.growthNow)}")
                c.growthBefore?.let { append(" (Vorquartal ${pct(it)})") }
                append(" · ${money(c.revenue)}/Quartal")
            }
            Text(growthText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
            c.reasons.filterNot { it.startsWith("Umsatz ") }.forEach {
                Text("• $it", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
