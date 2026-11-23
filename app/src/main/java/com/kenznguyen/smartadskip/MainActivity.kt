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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.kenznguyen.smartadskip.overlay.OverlayRequestRepository
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
    val currentSnapshot = snapshot
    val adState by AdDetectionRepository.state.collectAsState()
    val overlayRequested by OverlayRequestRepository.requested.collectAsState()
    val nowMillis by produceState(initialValue = System.currentTimeMillis(), currentSnapshot?.observedAtEpochMillis) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val snapshotAgeSeconds = currentSnapshot?.let {
        (nowMillis - it.observedAtEpochMillis).coerceAtLeast(0L) / 1_000L
    }
    val snapshotFresh = currentSnapshot != null &&
        (nowMillis - snapshot.observedAtEpochMillis) in 0..SNAPSHOT_STALE_AFTER_MILLIS
    val displayedAdState = if (snapshotFresh) adState.name else "UNKNOWN"
    val snapshotFreshness = when {
        currentSnapshot == null -> "Waiting for first event"
        snapshotFresh -> "Fresh · ${snapshotAgeSeconds}s ago"
        else -> "Stale · ${snapshotAgeSeconds}s ago"
    }
    val observedAt = currentSnapshot?.let {
        EVENT_TIME_FORMATTER.format(Instant.ofEpochMilli(it.observedAtEpochMillis))
    } ?: "—"
    val actionable = currentSnapshot?.actionableNodes
    val detectionStatus = when {
        !isConnected -> "ACCESSIBILITY DISCONNECTED"
        !snapshotFresh -> "STALE_SNAPSHOT"
        else -> actionable?.status?.name ?: "WAITING"
    }
    val verifiedCandidates = if (snapshotFresh) actionable?.candidates.orEmpty() else emptyList()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Smart Ad Skip", style = MaterialTheme.typography.headlineMedium)
        Text("P2.17 · Close-Only Minimal", style = MaterialTheme.typography.titleMedium)

        Text(
            "Accessibility: ${if (isConnected) "Connected" else "Not enabled"}",
            style = MaterialTheme.typography.bodyLarge
        )
        Button(onClick = {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }) {
            Text("Open Accessibility Settings")
        }

        Button(onClick = { OverlayRequestRepository.setRequested(!overlayRequested) }) {
            Text(if (overlayRequested) "Hide Floating Overlay" else "Show Floating Overlay")
        }
        Text(
            "Floating overlay: ${if (overlayRequested) "Requested" else "Off"}",
            style = MaterialTheme.typography.bodyMedium
        )

        Text("Detected ad state: $displayedAdState", style = MaterialTheme.typography.bodyLarge)
        Text("Latest observed package: ${currentSnapshot?.packageName ?: "—"}", style = MaterialTheme.typography.bodyMedium)
        Text("Last event: $observedAt", style = MaterialTheme.typography.bodyMedium)
        Text("Snapshot freshness: $snapshotFreshness", style = MaterialTheme.typography.bodyMedium)

        Text("Close button detection", style = MaterialTheme.typography.titleSmall)
        Text("Detection status: $detectionStatus", style = MaterialTheme.typography.bodyMedium)
        Text("Verified actionable nodes: ${verifiedCandidates.size}", style = MaterialTheme.typography.bodyMedium)
        Text("Allowed action: CLOSE only (X / Close / Dismiss)", style = MaterialTheme.typography.bodyMedium)

        Text("Click authorization", style = MaterialTheme.typography.titleSmall)
        Text("Strict Safety: enabled · Fail-closed", style = MaterialTheme.typography.bodyMedium)
        Text("Guards: Freshness · Target package · Live Accessibility node", style = MaterialTheme.typography.bodySmall)
        Text("Execution: Accessibility ACTION_CLICK only · No coordinate taps", style = MaterialTheme.typography.bodySmall)
        Text("Repeated attempts: One attempt per state", style = MaterialTheme.typography.bodySmall)
    }
}

private const val SNAPSHOT_STALE_AFTER_MILLIS = 5_000L
private val EVENT_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
