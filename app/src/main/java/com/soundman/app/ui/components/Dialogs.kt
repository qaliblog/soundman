package com.soundman.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.soundman.app.data.PersonLabel
import com.soundman.app.data.SoundLabel

@Composable
fun LabelDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Boolean, Long?) -> Unit,
    isPerson: Boolean,
    existingLabels: List<SoundLabel> = emptyList()
) {
    var labelName by remember { mutableStateOf("") }
    var useExistingLabel by remember { mutableStateOf(false) }
    var selectedLabelId by remember { mutableStateOf<Long?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isPerson) "Label Person" else "Label Sound") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!isPerson && existingLabels.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Use existing label")
                        Switch(
                            checked = useExistingLabel,
                            onCheckedChange = { 
                                useExistingLabel = it
                                if (!it) {
                                    selectedLabelId = null
                                    labelName = ""
                                }
                            }
                        )
                    }
                    
                    if (useExistingLabel) {
                        // Show dropdown for existing labels
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(
                                onClick = { expanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = existingLabels.find { it.id == selectedLabelId }?.name 
                                        ?: "Select existing label"
                                )
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                existingLabels.forEach { label ->
                                    DropdownMenuItem(
                                        text = { Text(label.name) },
                                        onClick = {
                                            selectedLabelId = label.id
                                            labelName = label.name
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = labelName,
                            onValueChange = { labelName = it },
                            label = { Text("New Label Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = labelName,
                        onValueChange = { labelName = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (labelName.isNotBlank() || (useExistingLabel && selectedLabelId != null)) {
                        onConfirm(labelName, useExistingLabel, selectedLabelId)
                    }
                }
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SoundSettingsDialog(
    soundLabel: SoundLabel,
    onDismiss: () -> Unit,
    onSave: (Float, Boolean, Boolean) -> Unit
) {
    var volume by remember { mutableStateOf(soundLabel.volumeMultiplier) }
    var isMuted by remember { mutableStateOf(soundLabel.isMuted) }
    var reverseTone by remember { mutableStateOf(soundLabel.reverseToneEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sound Settings: ${soundLabel.name}") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Volume: ${(volume * 100).toInt()}%")
                Slider(
                    value = volume,
                    onValueChange = { volume = it },
                    valueRange = 0f..2f
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Mute")
                    Switch(checked = isMuted, onCheckedChange = { isMuted = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Reverse Tone (Noise Cancellation)")
                    Switch(checked = reverseTone, onCheckedChange = { reverseTone = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(volume, isMuted, reverseTone) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun PersonSettingsDialog(
    personLabel: PersonLabel,
    onDismiss: () -> Unit,
    onSave: (Float, Boolean) -> Unit
) {
    var volume by remember { mutableStateOf(personLabel.volumeMultiplier) }
    var isMuted by remember { mutableStateOf(personLabel.isMuted) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Person Settings: ${personLabel.name}") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Volume: ${(volume * 100).toInt()}%")
                Slider(
                    value = volume,
                    onValueChange = { volume = it },
                    valueRange = 0f..2f
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Mute")
                    Switch(checked = isMuted, onCheckedChange = { isMuted = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(volume, isMuted) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
