package com.kenznguyen.smartadskip

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kenznguyen.smartadskip.accessibility.AccessibilityMonitorRepository
import com.kenznguyen.smartadskip.detection.AdDetectionRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AccessibilityMonitorScreen()
                }
            }
        }
    }
}

@Composable
private fun AccessibilityMonitorScreen() {
    val context = LocalContext.current
    val isConnected by AccessibilityMonitorRepository.isConnected.collectAsState()
    val snapshot by AccessibilityMonitorRepository.latestSnapshot.collectAsState()
    val adState by AdDetectionRepository.state.collectAsState()
    val nowMillis by produceState(initialValue = System.currentTimeMillis(), snapshot?.observedAtEpochMillis) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val snapshotAgeSeconds = snapshot?.let {
        (nowMillis - it.observedAtEpochMillis).coerceAtLeast(0L) / 1_000L
    } ?: 0L
    val snapshotFreshness = when {
        snapshot == null -> "Waiting for first event"
        snapshotAgeSeconds <= SNAPSHOT_STALE_AFTER_SECONDS -> "Fresh · ${snapshotAgeSeconds}s ago"
        else -> "Stale · ${snapshotAgeSeconds}s ago"
    }
    val displayedAdState = if (
        snapshot == null || snapshotAgeSeconds > SNAPSHOT_STALE_AFTER_SECONDS
    ) "UNKNOWN" else adState.name
    val observedAt = snapshot?.let {
        EVENT_TIME_FORMATTER.format(Instant.ofEpochMilli(it.observedAtEpochMillis))
    } ?: "—"

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Smart Ad Skip", style = MaterialTheme.typography.headlineMedium)
        Text("P2 · Ad State Detection", style = MaterialTheme.typography.titleMedium)
        Text("Monitor: ${if (isConnected) "Connected" else "Not enabled"}", style = MaterialTheme.typography.bodyLarge)
        Button(onClick = {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }) {
            Text("Open Accessibility Settings")
        }
        Text("Detected ad state: $displayedAdState", style = MaterialTheme.typography.bodyLarge)
        Text("Latest observed package: ${snapshot?.packageName ?: "—"}", style = MaterialTheme.typography.bodyMedium)
        Text("Window class: ${snapshot?.windowClassName ?: "—"}", style = MaterialTheme.typography.bodyMedium)
        Text("Event type: ${snapshot?.eventType?.toString() ?: "—"}", style = MaterialTheme.typography.bodyMedium)
        Text(
            "Structural nodes: ${snapshot?.structuralNodes?.size?.toString() ?: "—"} · Clickable: ${snapshot?.clickableNodeCount?.toString() ?: "—"}",
            style = MaterialTheme.typography.bodyMedium
        )
        Text("Last event: $observedAt", style = MaterialTheme.typography.bodyMedium)
        Text("Snapshot: $snapshotFreshness", style = MaterialTheme.typography.bodyMedium)
        Text("Safety: Strict · Observe only · No automatic actions", style = MaterialTheme.typography.bodyMedium)
        Text("Text, content descriptions, input values, screenshots and OCR are not collected.", style = MaterialTheme.typography.bodySmall)
    }
}

private const val SNAPSHOT_STALE_AFTER_SECONDS = 15L
private val EVENT_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
