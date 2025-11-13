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
import com.soundman.app.data.PersonLabel

@Composable
fun PersonLabelsList(
    personLabels: List<PersonLabel>,
    onSettingsClick: (PersonLabel) -> Unit,
    onToggleActive: ((Long, Boolean) -> Unit)? = null
) {
    if (personLabels.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No people labeled yet",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(personLabels) { person ->
                PersonLabelCard(
                    person = person,
                    onSettingsClick = { onSettingsClick(person) },
                    onToggleActive = onToggleActive
                )
            }
        }
    }
}

@Composable
fun PersonLabelCard(
    person: PersonLabel,
    onSettingsClick: () -> Unit,
    onToggleActive: ((Long, Boolean) -> Unit)? = null
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
                    text = person.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Detections: ${person.detectionCount}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Volume: ${(person.volumeMultiplier * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall
                )
                if (person.isMuted) {
                    Text(
                        text = "Muted",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (!person.transcription.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Transcription:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = person.transcription ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (onToggleActive != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Active", style = MaterialTheme.typography.bodySmall)
                        Switch(
                            checked = person.isActive,
                            onCheckedChange = { onToggleActive(person.id, it) }
                        )
                    }
                }
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }
    }
}
