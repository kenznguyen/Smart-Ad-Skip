package com.kenznguyen.smartadskip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FoundationScreen()
                }
            }
        }
    }
}

@Composable
private fun FoundationScreen() {
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
            text = "Proof of Concept · v0.1.0",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Foundation is ready for the next development phase.",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "Accessibility monitoring: not enabled",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "Automation: inactive",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "Safety mode: Strict",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
