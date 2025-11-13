package com.soundman.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
import com.soundman.app.ui.components.*
import com.soundman.app.viewmodel.SoundDetectionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundManApp(
    viewModel: SoundDetectionViewModel,
    onRequestPermission: () -> Unit
) {
    val isDetecting by viewModel.isDetecting.collectAsState()
    val currentDetection by viewModel.currentDetection.collectAsState()
    val unknownSoundCount by viewModel.unknownSoundCount.collectAsState()
    val soundLabels by viewModel.soundLabels.collectAsState(initial = emptyList())
    val activeSoundLabels by viewModel.activeSoundLabels.collectAsState(initial = emptyList())
    val inactiveSoundLabels by viewModel.inactiveSoundLabels.collectAsState(initial = emptyList())
    val personLabels by viewModel.personLabels.collectAsState(initial = emptyList())
    val isLiveMicEnabled by viewModel.isLiveMicEnabled.collectAsState()
    
    var showLanguageSettings by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf("en") }

    var showLabelDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf<Any?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    var selectedDetectionForLabel by remember { mutableStateOf<com.soundman.app.data.SoundDetection?>(null) }
    
    // Load unknown sound clusters
    val unknownSoundClusters = remember { mutableStateListOf<com.soundman.app.data.SoundDetection>() }
    
    LaunchedEffect(Unit, unknownSoundCount) {
        // Refresh clusters when unknown sounds are detected or on initial load
        val clusters = viewModel.getUnknownSoundClusters()
        unknownSoundClusters.clear()
        unknownSoundClusters.addAll(clusters)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SoundMan") },
                actions = {
                    IconButton(onClick = { showLanguageSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Language Settings")
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

            // Live Mic Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Live Mic Passthrough")
                Switch(
                    checked = isLiveMicEnabled,
                    onCheckedChange = { viewModel.setLiveMicEnabled(it) }
                )
            }

            // Tabs
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Active Sounds") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Inactive Sounds") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("People") }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { 
                        Row {
                            Text("Unknown")
                            if (unknownSoundClusters.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Badge {
                                    Text(unknownSoundClusters.size.toString())
                                }
                            }
                        }
                    }
                )
            }

            // Content
            when (selectedTab) {
                0 -> SoundLabelsList(
                    soundLabels = activeSoundLabels,
                    onSettingsClick = { showSettingsDialog = it },
                    onToggleActive = { id, active -> viewModel.toggleSoundActive(id, active) },
                    onToggleRecording = { id, recording -> viewModel.toggleSoundRecording(id, recording) }
                )
                1 -> SoundLabelsList(
                    soundLabels = inactiveSoundLabels,
                    onSettingsClick = { showSettingsDialog = it },
                    onToggleActive = { id, active -> viewModel.toggleSoundActive(id, active) },
                    onToggleRecording = { id, recording -> viewModel.toggleSoundRecording(id, recording) }
                )
                2 -> PersonLabelsList(
                    personLabels = personLabels,
                    onSettingsClick = { showSettingsDialog = it },
                    onToggleActive = { id, active -> viewModel.togglePersonActive(id, active) }
                )
                3 -> UnknownSoundsList(
                    unknownSoundClusters = unknownSoundClusters,
                    onLabelClick = { detection, _, _ ->
                        selectedDetectionForLabel = detection
                        showLabelDialog = true
                    }
                )
            }
        }
    }

    // Label Dialog
    if (showLabelDialog) {
        val detectionToLabel = selectedDetectionForLabel ?: currentDetection
        LabelDialog(
            onDismiss = { 
                showLabelDialog = false
                selectedDetectionForLabel = null
            },
            onConfirm = { labelName, useExisting, existingId ->
                if (detectionToLabel?.isPerson == true) {
                    viewModel.labelPerson(labelName)
                } else {
                    viewModel.labelUnknownSound(labelName, useExisting, existingId, detectionToLabel?.id)
                }
                showLabelDialog = false
                selectedDetectionForLabel = null
                // Refresh clusters after labeling - will be handled by LaunchedEffect
            },
            isPerson = detectionToLabel?.isPerson == true,
            existingLabels = soundLabels
        )
    }

    // Settings Dialog
    when (val settings = showSettingsDialog) {
        is com.soundman.app.data.SoundLabel -> {
            SoundSettingsDialog(
                soundLabel = settings,
                onDismiss = { showSettingsDialog = null },
                onSave = { volume, muted, reverseTone, isActive, isRecording ->
                    viewModel.updateSoundLabelSettings(
                        settings.id,
                        volume,
                        muted,
                        reverseTone,
                        isActive,
                        isRecording
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
                        muted,
                        null
                    )
                    showSettingsDialog = null
                }
            )
        }
    }

    // Language Settings Dialog
    if (showLanguageSettings) {
        AlertDialog(
            onDismissRequest = { showLanguageSettings = false },
            title = { Text("Transcription Language") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Select language for speech transcription:")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedLanguage == "en",
                                onClick = { selectedLanguage = "en" }
                            )
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedLanguage == "en",
                            onClick = { selectedLanguage = "en" }
                        )
                        Text("English", modifier = Modifier.padding(start = 8.dp))
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedLanguage == "fa",
                                onClick = { selectedLanguage = "fa" }
                            )
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedLanguage == "fa",
                            onClick = { selectedLanguage = "fa" }
                        )
                        Text("Persian (فارسی)", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setTranscriptionLanguage(selectedLanguage)
                        showLanguageSettings = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLanguageSettings = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
