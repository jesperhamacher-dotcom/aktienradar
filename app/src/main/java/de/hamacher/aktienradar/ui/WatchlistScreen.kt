package de.hamacher.aktienradar.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import de.hamacher.aktienradar.data.Analyzer
import de.hamacher.aktienradar.data.Repo
import de.hamacher.aktienradar.data.WatchItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(onOpen: (String) -> Unit, onSettings: () -> Unit) {
    val list by Repo.items.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var busy by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aktienradar") },
                actions = {
                    IconButton(enabled = !busy && list.isNotEmpty(), onClick = {
                        scope.launch {
                            busy = true
                            for (item in Repo.items.value) {
                                Repo.refresh(item.ticker)
                                delay(250)
                            }
                            busy = false
                        }
                    }) { Icon(Icons.Default.Refresh, contentDescription = "Alle aktualisieren") }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Einstellungen")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, contentDescription = "Aktie hinzufügen")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (list.isEmpty()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Noch keine Aktien in der Watchlist.", style = MaterialTheme.typography.titleMedium)
                    Text("Tippe auf +, um ein US-Ticker-Symbol hinzuzufügen, z. B. MSFT, COST oder CRWD.")
                    Text(DISCLAIMER, style = MaterialTheme.typography.bodySmall)
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(list, key = { it.ticker }) { item ->
                    WatchCard(item) { onOpen(item.ticker) }
                }
            }
        }
    }

    if (showAdd) {
        AddTickerDialog(
            onDismiss = { showAdd = false },
            onAdd = { ticker ->
                showAdd = false
                scope.launch {
                    busy = true
                    val error = Repo.add(context, ticker)
                    busy = false
                    if (error != null) snackbar.showSnackbar(error)
                }
            },
        )
    }
}

@Composable
private fun WatchCard(item: WatchItem, onClick: () -> Unit) {
    val analysis = remember(item.quarters) { Analyzer.analyze(item.quarters) }
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ScoreBadge(analysis)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.ticker, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(item.name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                Text("${analysis.level.emoji} ${analysis.level.label}", style = MaterialTheme.typography.bodyMedium)
                if (item.brokenIds.isNotEmpty()) {
                    Text(
                        "⚠️ ${item.brokenIds.size} Thesen-Bedingung(en) verletzt",
                        color = NegativeColor,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                item.error?.let {
                    Text("Fehler: $it", color = NegativeColor, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun AddTickerDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aktie hinzufügen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.uppercase().trim() },
                    label = { Text("Ticker-Symbol") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
                Text(
                    "Unterstützt werden Unternehmen, die bei der US-Börsenaufsicht SEC berichten.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onAdd(text) }) { Text("Hinzufügen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
