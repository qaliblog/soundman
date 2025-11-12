package com.soundman.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.soundman.app.data.PersonLabel
import com.soundman.app.data.SoundLabel

@Composable
fun LabelDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    isPerson: Boolean
) {
    var labelName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isPerson) "Label Person" else "Label Sound") },
        text = {
            OutlinedTextField(
                value = labelName,
                onValueChange = { labelName = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (labelName.isNotBlank()) {
                        onConfirm(labelName)
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
