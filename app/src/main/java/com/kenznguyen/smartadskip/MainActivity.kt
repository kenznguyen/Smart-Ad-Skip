package com.kenznguyen.smartadskip

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kenznguyen.smartadskip.accessibility.AccessibilityMonitorRepository
import com.kenznguyen.smartadskip.ai.AiRuntimeDashboardStatus
import com.kenznguyen.smartadskip.ai.QwenRuntimePhase
import com.kenznguyen.smartadskip.ai.ModelPackageImporter
import com.kenznguyen.smartadskip.accessibility.AccessibilitySnapshotPolicy
import com.kenznguyen.smartadskip.detection.AdDetectionRepository
import com.kenznguyen.smartadskip.overlay.OverlayRequestRepository
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val scope = rememberCoroutineScope()
    var modelImportStatus by remember {
        mutableStateOf(
            if (File(context.filesDir, "models/" + ModelPackageImporter.MODEL_DIRECTORY + "/" + ModelPackageImporter.MODEL_FILE).isFile) {
                "Qwen3-VL model package is installed"
            } else {
                "Qwen3-VL model not imported"
            }
        )
    }
    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                modelImportStatus = "Importing and verifying model package…"
                modelImportStatus = runCatching {
                    withContext(Dispatchers.IO) {
                        val input = context.contentResolver.openInputStream(uri)
                            ?: error("Cannot open selected model package")
                        input.use {
                            ModelPackageImporter(File(context.filesDir, "models")).importPackage(it)
                        }
                    }
                    "Qwen3-VL model imported and verified"
                }.getOrElse { "Model import failed: " + (it.message ?: "invalid package") }
            }
        }
    }
    val aiRuntimeStatus by AiRuntimeDashboardStatus.state.collectAsState()
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
    } ?: 0L
    val snapshotFresh = currentSnapshot != null && (nowMillis - currentSnapshot.observedAtEpochMillis) in 0..SNAPSHOT_STALE_AFTER_MILLIS
    val snapshotFreshness = when {
        currentSnapshot == null -> "Waiting for first event"
        snapshotFresh -> "Fresh · ${snapshotAgeSeconds}s ago"
        else -> "Stale · ${snapshotAgeSeconds}s ago"
    }
    val diagnosticNodes = currentSnapshot?.let { AccessibilitySnapshotPolicy().diagnosticNodes(it) }.orEmpty()
    val displayedAdState = if (!snapshotFresh) "UNKNOWN" else adState.name
    val observedAt = currentSnapshot?.let {
        EVENT_TIME_FORMATTER.format(Instant.ofEpochMilli(it.observedAtEpochMillis))
    } ?: "—"

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Smart Ad Skip", style = MaterialTheme.typography.headlineMedium)
        Text("P2.11 · Scrollable Dashboard + Floating Overlay", style = MaterialTheme.typography.titleMedium)
        Text("Monitor: ${if (isConnected) "Connected" else "Not enabled"}", style = MaterialTheme.typography.bodyLarge)
        Button(onClick = { modelPicker.launch(arrayOf("application/zip", "application/octet-stream")) }) {
            Text("Import Qwen3-VL model ZIP")
        }

        Text(modelImportStatus, style = MaterialTheme.typography.bodySmall)
        Text("P2.17 · Autonomous AI Runtime", style = MaterialTheme.typography.titleMedium)
        val modelFilesPresent = File(
            context.filesDir,
            "models/" + ModelPackageImporter.MODEL_DIRECTORY + "/" + ModelPackageImporter.MODEL_FILE
        ).isFile
        val runtimeLabel = when (aiRuntimeStatus.phase) {
            QwenRuntimePhase.NOT_IMPORTED -> if (modelFilesPresent) {
                if (isConnected) "IMPORTED · SERVICE RECONNECT REQUIRED" else "IMPORTED · WAITING FOR ACCESSIBILITY SERVICE"
            } else "NOT IMPORTED"
            QwenRuntimePhase.LOADING -> "LOADING"
            QwenRuntimePhase.READY -> "READY"
            QwenRuntimePhase.FAILED -> "FAILED"
        }
        Text("Qwen3-VL runtime: ${runtimeLabel}", style = MaterialTheme.typography.bodyLarge)
        Text("Runtime detail: ${aiRuntimeStatus.detail}", style = MaterialTheme.typography.bodySmall)
        Text(
            "Inference settings: CPU ${aiRuntimeStatus.config.cpuThreads} threads · context ${aiRuntimeStatus.config.contextSize} · batch ${aiRuntimeStatus.config.batchSize}",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            "Image edge: ${aiRuntimeStatus.config.maxImageEdge}px · timeout ${aiRuntimeStatus.config.inferenceTimeoutMillis}ms · confidence ≥ 0.85 · frame age ≤ 1500ms",
            style = MaterialTheme.typography.bodySmall
        )
        Text("Latest model decision: ${aiRuntimeStatus.lastAction}", style = MaterialTheme.typography.bodyMedium)
        Text("Decision reason: ${aiRuntimeStatus.lastReason}", style = MaterialTheme.typography.bodySmall)
        Text(
            "Frame ID: ${if (aiRuntimeStatus.lastFrameId > 0L) aiRuntimeStatus.lastFrameId.toString() else "—"} · decisions ${aiRuntimeStatus.inferenceCount} · WAIT ${aiRuntimeStatus.waitCount} · action suggestions ${aiRuntimeStatus.actionCount}",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            "Model action suggestions are not confirmed clicks. Shared safety checks and live Accessibility-node validation remain mandatory.",
            style = MaterialTheme.typography.bodySmall
        )
        Button(onClick = {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }) {
            Text("Open Accessibility Settings")
        }
        Button(onClick = { OverlayRequestRepository.setRequested(!overlayRequested) }) {
            Text(if (overlayRequested) "Hide Floating Overlay" else "Show Floating Overlay")
        }
        Text("Floating overlay: ${if (overlayRequested) "Requested" else "Off"}", style = MaterialTheme.typography.bodyMedium)
        Text("Detected ad state: $displayedAdState", style = MaterialTheme.typography.bodyLarge)
        Text("Latest observed package: ${currentSnapshot?.packageName ?: "—"}", style = MaterialTheme.typography.bodyMedium)
        Text("Window class: ${currentSnapshot?.windowClassName ?: "—"}", style = MaterialTheme.typography.bodyMedium)
        Text("Event type: ${currentSnapshot?.eventType?.toString() ?: "—"}", style = MaterialTheme.typography.bodyMedium)
        Text(
            "Structural nodes: ${currentSnapshot?.structuralNodes?.size?.toString() ?: "—"} · Clickable: ${currentSnapshot?.clickableNodeCount?.toString() ?: "—"}",
            style = MaterialTheme.typography.bodyMedium
        )
        Text("Actionable UI detection", style = MaterialTheme.typography.titleSmall)
        val actionable = currentSnapshot?.actionableNodes
        Text(
            "Detection: ${if (snapshotFresh) actionable?.status?.name ?: "WAITING" else "STALE_SNAPSHOT"}",
            style = MaterialTheme.typography.bodySmall
        )
        val candidates = if (snapshotFresh) actionable?.candidates.orEmpty() else emptyList()
        if (candidates.isEmpty()) {
            Text("No verified actionable controls in the latest currentSnapshot", style = MaterialTheme.typography.bodySmall)
        } else {
            candidates.forEachIndexed { index, candidate ->
                Text(
                    "${index + 1}. Visual candidate · bounds=${candidate.bounds}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Text("Visual actions: NEXT_AD / CLOSE · Strict Safety gated", style = MaterialTheme.typography.bodySmall)
        Text("Structural transition diagnostics", style = MaterialTheme.typography.titleSmall)
        val transition = currentSnapshot?.structuralTransition
        if (transition?.comparisonAvailable == true) {
            Text("Window class changed: ${transition.windowClassChanged}", style = MaterialTheme.typography.bodySmall)
            Text("Structural node delta: ${transition.structuralNodeDelta}", style = MaterialTheme.typography.bodySmall)
            Text("Clickable node delta: ${transition.clickableNodeDelta}", style = MaterialTheme.typography.bodySmall)
        } else {
            Text("Baseline / insufficient fresh same-package history", style = MaterialTheme.typography.bodySmall)
        }
        Text("Temporal pattern diagnostics (max 10 samples)", style = MaterialTheme.typography.titleSmall)
        val temporal = currentSnapshot?.temporalPattern
        if (temporal?.historyAvailable == true) {
            Text("Samples: ${temporal.sampleCount}", style = MaterialTheme.typography.bodySmall)
            Text("Window transitions: ${temporal.windowTransitionCount}", style = MaterialTheme.typography.bodySmall)
            Text("Observed span: ${temporal.observedSpanMillis} ms", style = MaterialTheme.typography.bodySmall)
            Text("Current window stable: ${temporal.currentWindowStableMillis} ms", style = MaterialTheme.typography.bodySmall)
        } else {
            Text("Waiting for fresh same-package temporal baseline", style = MaterialTheme.typography.bodySmall)
        }
        Text("Temporal metrics are diagnostic only; they never establish ad state.", style = MaterialTheme.typography.bodySmall)
        Text("Structural diagnostics (max 20)", style = MaterialTheme.typography.titleSmall)
        if (diagnosticNodes.isEmpty()) {
            Text("No structural metadata available", style = MaterialTheme.typography.bodySmall)
        } else {
            diagnosticNodes.forEachIndexed { index, node ->
                Text(
                    "${index + 1}. ${node.viewClassName ?: "—"} · ${node.resourceId ?: "no resource id"} · clickable=${node.clickable} · enabled=${node.enabled} · visible=${node.visible}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Text("Last event: $observedAt", style = MaterialTheme.typography.bodyMedium)
        Text("Snapshot: $snapshotFreshness", style = MaterialTheme.typography.bodyMedium)
        Text("Safety: AI-only NEXT_AD/CLOSE · protected destinations and completed-ad contexts are blocked", style = MaterialTheme.typography.bodyMedium)
        Text("Screenshots and bounded clickable-node descriptions are processed locally by Qwen3-VL; no screen data is sent to a network service.", style = MaterialTheme.typography.bodySmall)
    }
}

private const val SNAPSHOT_STALE_AFTER_MILLIS = 5_000L
private val EVENT_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
