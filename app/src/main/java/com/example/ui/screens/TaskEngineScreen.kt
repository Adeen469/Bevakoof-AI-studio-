package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AgentTask
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonCritical
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.IndigoNeon
import com.example.ui.viewmodel.BewakoofViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TaskEngineScreen(
    viewModel: BewakoofViewModel,
    modifier: Modifier = Modifier
) {
    val allTasks by viewModel.allTasks.collectAsState()
    val activeTasks by viewModel.activeTasks.collectAsState()

    var showActiveOnly by remember { mutableStateOf(false) }

    val displayTasks = if (showActiveOnly) activeTasks else allTasks

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "TASK ENGINE",
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            color = CyanNeon,
            letterSpacing = 1.sp
        )
        Text(
            text = "State machine, verification pipeline, and idempotency tracking.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Filter chips
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !showActiveOnly,
                onClick = { showActiveOnly = false },
                label = { Text("All Tasks (${allTasks.size})") },
                modifier = Modifier.testTag("filter_all_tasks")
            )
            FilterChip(
                selected = showActiveOnly,
                onClick = { showActiveOnly = true },
                label = { Text("Active (${activeTasks.size})") },
                modifier = Modifier.testTag("filter_active_tasks")
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (displayTasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.PendingActions,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (showActiveOnly) "No active tasks in flight." else "No tasks executed yet.",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Give a voice goal on Home screen to initiate task planning.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(displayTasks, key = { it.id }) { task ->
                    TaskCard(task)
                }
            }
        }
    }
}

@Composable
fun TaskCard(task: AgentTask) {
    val statusColor = when (task.status) {
        "COMPLETED" -> EmeraldSafe
        "EXECUTING", "PLANNING" -> CyanNeon
        "FAILED", "CANCELLED" -> CrimsonCritical
        "WAITING_FOR_PERMISSION", "WAITING_FOR_CONFIRMATION" -> AmberWarning
        else -> IndigoNeon
    }

    val riskColor = when (task.riskLevel) {
        "L0_SAFE" -> EmeraldSafe
        "L1_CONFIRMATION" -> CyanNeon
        "L2_SENSITIVE" -> AmberWarning
        "L3_CRITICAL" -> CrimsonCritical
        else -> IndigoNeon
    }

    val timeFormatted = remember(task.createdAt) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(task.createdAt))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task_card_${task.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Goal & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = task.userGoal,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = task.status,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Plan Summary
            Text(
                text = task.planSummary,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Result or Error if present
            if (task.resultSummary.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldSafe, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = task.resultSummary,
                        fontSize = 12.sp,
                        color = EmeraldSafe
                    )
                }
            }

            if (task.errorMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Error, contentDescription = null, tint = CrimsonCritical, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = task.errorMessage,
                        fontSize = 12.sp,
                        color = CrimsonCritical
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Metadata footer: Risk, Tool, Idempotency Op ID, Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = riskColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = task.riskLevel,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = riskColor,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }

                    if (task.toolName.isNotBlank()) {
                        Surface(
                            color = IndigoNeon.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = task.toolName,
                                fontSize = 9.sp,
                                color = IndigoNeon,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "op:${task.operationId.take(6)}...",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = timeFormatted,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}
