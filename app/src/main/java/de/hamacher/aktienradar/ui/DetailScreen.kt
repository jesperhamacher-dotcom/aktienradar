package de.hamacher.aktienradar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.hamacher.aktienradar.data.Analysis
import de.hamacher.aktienradar.data.Analyzer
import de.hamacher.aktienradar.data.Condition
import de.hamacher.aktienradar.data.Metric
import de.hamacher.aktienradar.data.QuarterMetrics
import de.hamacher.aktienradar.data.Repo
import de.hamacher.aktienradar.data.WatchItem
import de.hamacher.aktienradar.data.fmtNumber
import de.hamacher.aktienradar.data.money
import de.hamacher.aktienradar.data.pct
import de.hamacher.aktienradar.data.quarterLabel
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(ticker: String, onBack: () -> Unit) {
    val list by Repo.items.collectAsState()
    val item = list.firstOrNull { it.ticker == ticker }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showAddCondition by remember { mutableStateOf(false) }

    if (item == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val analysis = remember(item.quarters) { Analyzer.analyze(item.quarters) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item.ticker) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(enabled = !busy, onClick = {
                        scope.launch { busy = true; Repo.refresh(ticker); busy = false }
                    }) { Icon(Icons.Default.Refresh, contentDescription = "Aktualisieren") }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Entfernen")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Header(item, analysis)
            item.error?.let { Text("Fehler bei der letzten Aktualisierung: $it", color = NegativeColor) }
            SignalsCard(analysis)
            if (analysis.metrics.any { it.revGrowth != null }) GrowthChart(analysis.metrics)
            QuarterTable(analysis.metrics)
            ThesisCard(
                item = item,
                analysis = analysis,
                onSaveNote = { note -> scope.launch { Repo.setThesisNote(ticker, note) } },
                onAddCondition = { showAddCondition = true },
                onRemoveCondition = { id -> scope.launch { Repo.removeCondition(ticker, id) } },
            )
            Text(DISCLAIMER, style = MaterialTheme.typography.bodySmall)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("${item.ticker} entfernen?") },
            text = { Text("Die Aktie und deine These werden aus der Watchlist gelöscht.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { Repo.remove(ticker) }
                    onBack()
                }) { Text("Entfernen") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }

    if (showAddCondition) {
        AddConditionDialog(
            onDismiss = { showAddCondition = false },
            onAdd = { c ->
                showAddCondition = false
                scope.launch { Repo.addCondition(ticker, c) }
            },
        )
    }
}

@Composable
private fun Header(item: WatchItem, analysis: Analysis) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ScoreBadge(analysis, size = 72.dp)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(item.name, style = MaterialTheme.typography.titleMedium)
            Text("${analysis.level.emoji} ${analysis.level.label}", style = MaterialTheme.typography.bodyLarge)
            if (item.lastUpdated > 0) {
                val time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.lastUpdated))
                Text("Stand: $time", style = MaterialTheme.typography.bodySmall)
            }
            analysis.latest?.let {
                Text("Letztes Quartal: ${quarterLabel(it.end)} (bis ${it.end})", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SignalsCard(analysis: Analysis) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Warum dieser Score?", style = MaterialTheme.typography.titleMedium)
            if (analysis.signals.isEmpty()) {
                Text("Keine auffälligen Veränderungen erkannt oder zu wenig Quartalsdaten.")
            }
            analysis.signals.forEach { s ->
                Row {
                    Text(
                        if (s.positive) "▲ " else "▼ ",
                        color = if (s.positive) PositiveColor else NegativeColor,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(s.text, Modifier.weight(1f))
                    Text(
                        if (s.points > 0) "+${s.points}" else s.points.toString(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Text(
                "Startwert 50 Punkte, plus/minus der einzelnen Signale.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun GrowthChart(metrics: List<QuarterMetrics>) {
    val data = metrics.filter { it.revGrowth != null }.takeLast(12)
    val positive = PositiveColor
    val negative = NegativeColor
    val axis = MaterialTheme.colorScheme.outline
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Umsatzwachstum ggü. Vorjahresquartal", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                val values = data.map { it.revGrowth!! }
                val maxV = maxOf(values.maxOrNull() ?: 0.0, 0.0)
                val minV = minOf(values.minOrNull() ?: 0.0, 0.0)
                val range = (maxV - minV).takeIf { it > 0 } ?: 1.0
                val zeroY = (maxV / range * size.height).toFloat()
                val slot = size.width / values.size
                val barW = slot * 0.6f
                values.forEachIndexed { i, v ->
                    val h = (kotlin.math.abs(v) / range * size.height).toFloat()
                    val x = i * slot + (slot - barW) / 2
                    val top = if (v >= 0) zeroY - h else zeroY
                    drawRect(
                        color = if (v >= 0) positive else negative,
                        topLeft = Offset(x, top),
                        size = Size(barW, h),
                    )
                }
                drawLine(axis, Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 2f)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(data.firstOrNull()?.let { quarterLabel(it.end) } ?: "", style = MaterialTheme.typography.bodySmall)
                Text(data.lastOrNull()?.let { "${quarterLabel(it.end)}: ${pct(it.revGrowth!!)}" } ?: "", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun QuarterTable(metrics: List<QuarterMetrics>) {
    val rows = metrics.takeLast(8).reversed()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Quartalsverlauf", style = MaterialTheme.typography.titleMedium)
            TableRow("Quartal", "Umsatz", "Wachst.", "Op.-Marge", "FCF", header = true)
            HorizontalDivider()
            rows.forEach { m ->
                TableRow(
                    quarterLabel(m.end),
                    m.revenue?.let { money(it) } ?: "–",
                    m.revGrowth?.let { pct(it) } ?: "–",
                    m.opMargin?.let { pct(it, sign = false) } ?: "–",
                    m.fcf?.let { money(it) } ?: "–",
                )
            }
            Text(
                "FCF = operativer Cashflow minus Investitionen in Sachanlagen. Werte aus SEC-Meldungen, Quartale teils aus Jahres- bzw. Halbjahreswerten abgeleitet.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun TableRow(vararg cells: String, header: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        cells.forEachIndexed { i, c ->
            Text(
                c,
                modifier = Modifier.weight(if (i == 0) 1.1f else 1f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ThesisCard(
    item: WatchItem,
    analysis: Analysis,
    onSaveNote: (String) -> Unit,
    onAddCondition: () -> Unit,
    onRemoveCondition: (String) -> Unit,
) {
    var note by remember(item.ticker) { mutableStateOf(item.thesisNote) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Meine Investment-These", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = { Text("z. B. Ich kaufe, weil Umsatz und FCF über Jahre > 20 % wachsen sollen.") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            if (note != item.thesisNote) {
                OutlinedButton(onClick = { onSaveNote(note) }) { Text("Notiz speichern") }
            }

            Text("Bedingungen, die erfüllt bleiben müssen", fontWeight = FontWeight.Bold)
            if (item.conditions.isEmpty()) {
                Text(
                    "Noch keine Bedingungen. Die App warnt dich, sobald eine Bedingung nach neuen Quartalszahlen nicht mehr erfüllt ist.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            item.conditions.forEach { c ->
                val ok = c.evaluate(analysis)
                val current = analysis.value(c.metric)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (ok) { true -> "✅"; false -> "⚠️"; null -> "❔" },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(c.describe())
                        Text(
                            "Aktuell: " + (current?.let { "${fmtNumber(it)} ${c.metric.unit}" } ?: "keine Daten"),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (ok == false) NegativeColor else Color.Unspecified,
                        )
                    }
                    IconButton(onClick = { onRemoveCondition(c.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Bedingung löschen")
                    }
                }
            }
            OutlinedButton(onClick = onAddCondition) { Text("Bedingung hinzufügen") }
        }
    }
}

@Composable
private fun AddConditionDialog(onDismiss: () -> Unit, onAdd: (Condition) -> Unit) {
    var metric by remember { mutableStateOf(Metric.REV_GROWTH) }
    var atLeast by remember { mutableStateOf(true) }
    var value by remember { mutableStateOf("") }
    val parsed = value.replace(',', '.').toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bedingung hinzufügen") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Metric.entries.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = metric == m, onClick = { metric = m }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = metric == m, onClick = { metric = m })
                        Text(m.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = atLeast, onClick = { atLeast = true }, label = { Text("mindestens ≥") })
                    FilterChip(selected = !atLeast, onClick = { atLeast = false }, label = { Text("höchstens ≤") })
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Grenzwert (${metric.unit})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Text(
                    "Beispiel: Umsatzwachstum mindestens 20 %, Aktienanzahl höchstens 2 %.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = parsed != null, onClick = {
                onAdd(Condition(metric = metric, atLeast = atLeast, threshold = parsed!!))
            }) { Text("Hinzufügen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
