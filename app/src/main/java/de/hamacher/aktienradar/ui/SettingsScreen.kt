package de.hamacher.aktienradar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.hamacher.aktienradar.data.Repo
import de.hamacher.aktienradar.work.CheckWorker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var email by remember { mutableStateOf(Repo.contactEmail) }
    var info by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Kontakt-E-Mail für die SEC", style = MaterialTheme.typography.titleMedium)
            Text(
                "Die US-Börsenaufsicht SEC verlangt bei jeder Datenabfrage eine Kontaktadresse. " +
                    "Sie wird nur im Anfrage-Header an sec.gov gesendet.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("E-Mail") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = {
                if (email.contains("@")) {
                    Repo.contactEmail = email
                    onBack()
                } else {
                    info = "Bitte eine gültige E-Mail-Adresse eingeben."
                }
            }) { Text("Speichern") }

            Text("Hintergrundprüfung", style = MaterialTheme.typography.titleMedium)
            Text(
                "Die App prüft deine Watchlist automatisch etwa alle 12 Stunden (bei Internetverbindung) " +
                    "und meldet sich bei Score-Änderungen ab 10 Punkten oder wenn eine Thesen-Bedingung nicht mehr erfüllt ist.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = {
                CheckWorker.runNow(context)
                info = "Prüfung gestartet."
            }) { Text("Jetzt prüfen") }

            info?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

            Text("Hinweis", style = MaterialTheme.typography.titleMedium)
            Text(DISCLAIMER, style = MaterialTheme.typography.bodySmall)
        }
    }
}
