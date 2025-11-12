package com.soundman.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.soundman.app.data.SoundLabel

@Composable
fun SoundLabelsList(
    soundLabels: List<SoundLabel>,
    onSettingsClick: (SoundLabel) -> Unit
) {
    if (soundLabels.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No sounds labeled yet",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(soundLabels) { label ->
                SoundLabelCard(
                    label = label,
                    onSettingsClick = { onSettingsClick(label) }
                )
            }
        }
    }
}

@Composable
fun SoundLabelCard(
    label: SoundLabel,
    onSettingsClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Detections: ${label.detectionCount}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Volume: ${(label.volumeMultiplier * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall
                )
                if (label.isMuted) {
                    Text(
                        text = "Muted",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (label.reverseToneEnabled) {
                    Text(
                        text = "Reverse Tone: ON",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }
    }
}
