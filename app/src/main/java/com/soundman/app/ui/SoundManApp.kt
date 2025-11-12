package com.soundman.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.soundman.app.ui.components.*
import com.soundman.app.viewmodel.SoundDetectionViewModel

@Composable
fun SoundManApp(
    viewModel: SoundDetectionViewModel,
    onRequestPermission: () -> Unit
) {
    val isDetecting by viewModel.isDetecting.collectAsState()
    val currentDetection by viewModel.currentDetection.collectAsState()
    val unknownSoundCount by viewModel.unknownSoundCount.collectAsState()
    val soundLabels by viewModel.soundLabels.collectAsState(initial = emptyList())
    val personLabels by viewModel.personLabels.collectAsState(initial = emptyList())

    var showLabelDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf<Any?>(null) }
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SoundMan") },
                actions = {
                    IconButton(onClick = { selectedTab = 1 }) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Detection Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isDetecting) "Detection Active" else "Detection Inactive",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (currentDetection != null) {
                        Text(
                            text = currentDetection!!.labelName ?: "Unknown Sound",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (currentDetection!!.labelName != null) {
                            LinearProgressIndicator(
                                progress = currentDetection!!.confidence,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "Confidence: ${(currentDetection!!.confidence * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    
                    if (unknownSoundCount > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Unknown sounds detected: $unknownSoundCount",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Control Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = {
                        if (isDetecting) {
                            viewModel.stopDetection()
                        } else {
                            onRequestPermission()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDetecting) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        if (isDetecting) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isDetecting) "Stop" else "Start")
                }

                if (unknownSoundCount > 0) {
                    Button(
                        onClick = { showLabelDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Label, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Label")
                    }
                }
            }

            // Tabs
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Sounds") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("People") }
                )
            }

            // Content
            when (selectedTab) {
                0 -> SoundLabelsList(
                    soundLabels = soundLabels,
                    onSettingsClick = { showSettingsDialog = it }
                )
                1 -> PersonLabelsList(
                    personLabels = personLabels,
                    onSettingsClick = { showSettingsDialog = it }
                )
            }
        }
    }

    // Label Dialog
    if (showLabelDialog) {
        LabelDialog(
            onDismiss = { showLabelDialog = false },
            onConfirm = { labelName ->
                if (currentDetection?.isPerson == true) {
                    viewModel.labelPerson(labelName)
                } else {
                    viewModel.labelUnknownSound(labelName)
                }
                showLabelDialog = false
            },
            isPerson = currentDetection?.isPerson == true
        )
    }

    // Settings Dialog
    when (val settings = showSettingsDialog) {
        is com.soundman.app.data.SoundLabel -> {
            SoundSettingsDialog(
                soundLabel = settings,
                onDismiss = { showSettingsDialog = null },
                onSave = { volume, muted, reverseTone ->
                    viewModel.updateSoundLabelSettings(
                        settings.id,
                        volume,
                        muted,
                        reverseTone
                    )
                    showSettingsDialog = null
                }
            )
        }
        is com.soundman.app.data.PersonLabel -> {
            PersonSettingsDialog(
                personLabel = settings,
                onDismiss = { showSettingsDialog = null },
                onSave = { volume, muted ->
                    viewModel.updatePersonSettings(
                        settings.id,
                        volume,
                        muted
                    )
                    showSettingsDialog = null
                }
            )
        }
    }
}
