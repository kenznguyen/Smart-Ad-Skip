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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kenznguyen.smartadskip.accessibility.AccessibilityMonitorRepository

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Smart Ad Skip",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "P1 · Accessibility Monitor",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Monitor: ${if (isConnected) "Connected" else "Not enabled"}",
            style = MaterialTheme.typography.bodyLarge
        )
        Button(onClick = {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }) {
            Text("Open Accessibility Settings")
        }
        Text(
            text = "Active window package: ${snapshot?.packageName ?: "—"}",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "Activity (window-state event): ${snapshot?.activityClassName ?: "—"}",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "Event type: ${snapshot?.eventType?.toString() ?: "—"}",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "UI nodes: ${snapshot?.nodeCount?.toString() ?: "—"} · Clickable nodes: ${snapshot?.clickableNodeCount?.toString() ?: "—"}",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "Safety: Strict · Observe only · No automatic actions",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "On-screen text and input values are not collected.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
