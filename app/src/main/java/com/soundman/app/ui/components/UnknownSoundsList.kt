package com.soundman.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Label
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.soundman.app.data.SoundDetection
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun UnknownSoundsList(
    unknownSoundClusters: List<SoundDetection>,
    onLabelClick: (SoundDetection, String?, Long?) -> Unit
) {
    if (unknownSoundClusters.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No unknown sounds detected yet",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    } else {
        // Group by clusterId
        val groupedByCluster = unknownSoundClusters.groupBy { it.clusterId ?: "unclustered" }
        
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            groupedByCluster.forEach { (clusterId, detections) ->
                item {
                    UnknownSoundClusterCard(
                        clusterId = clusterId,
                        detections = detections,
                        onLabelClick = onLabelClick
                    )
                }
            }
        }
    }
}

@Composable
fun UnknownSoundClusterCard(
    clusterId: String,
    detections: List<SoundDetection>,
    onLabelClick: (SoundDetection, String?, Long?) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (clusterId == "unclustered") "Unclustered Sounds" else "Sound Group ${clusterId.substringAfterLast("_").take(8)}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${detections.size} similar sound${if (detections.size > 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Label entire cluster button
                if (detections.isNotEmpty()) {
                    TextButton(
                        onClick = { 
                            // Label the first detection (will label entire cluster)
                            onLabelClick(detections.first(), null, null)
                        }
                    ) {
                        Icon(Icons.Default.Label, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Label Group")
                    }
                }
            }
            
            Divider()
            
            // List individual detections in this cluster
            detections.take(5).forEach { detection ->
                UnknownSoundItem(
                    detection = detection,
                    onLabelClick = onLabelClick
                )
            }
            
            if (detections.size > 5) {
                Text(
                    text = "... and ${detections.size - 5} more",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }
    }
}

@Composable
fun UnknownSoundItem(
    detection: SoundDetection,
    onLabelClick: (SoundDetection, String?, Long?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Unknown Sound",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = formatTimestamp(detection.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (detection.frequency != null) {
                        Text(
                            text = "Frequency: ${String.format("%.1f", detection.frequency)} Hz",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (detection.duration != null) {
                        Text(
                            text = "Duration: ${detection.duration}ms",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
        }
        
        TextButton(
            onClick = { onLabelClick(detection, null, null) }
        ) {
            Text("Label")
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
