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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.IndigoNeon
import com.example.ui.viewmodel.BewakoofViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MemoryScreen(
    viewModel: BewakoofViewModel,
    modifier: Modifier = Modifier
) {
    val memories by viewModel.allMemories.collectAsState()
    val routines by viewModel.allRoutines.collectAsState()
    val activityLogs by viewModel.recentActivity.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showAddMemoryDialog by remember { mutableStateOf(false) }
    var showAddRoutineDialog by remember { mutableStateOf(false) }

    val filteredMemories = remember(memories, searchQuery) {
        if (searchQuery.isBlank()) memories
        else memories.filter {
            it.memoryKey.contains(searchQuery, ignoreCase = true) ||
                    it.content.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "MEMORY & TIMELINE",
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            color = CyanNeon,
            letterSpacing = 1.sp
        )
        Text(
            text = "Semantic knowledge, automated routines, and audited activity log.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 1. Semantic Memory Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bookmark, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Learned Facts & Preferences", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }

                    IconButton(
                        onClick = { showAddMemoryDialog = true },
                        modifier = Modifier.testTag("add_memory_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Memory", tint = CyanNeon)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search memories...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("memory_search_field")
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (filteredMemories.isEmpty()) {
                    Text("No memories found.", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                } else {
                    filteredMemories.forEach { mem ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Surface(
                                    color = IndigoNeon.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(mem.category, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = IndigoNeon, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(mem.memoryKey, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(mem.content, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { viewModel.deleteMemory(mem.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Automated Routines Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = EmeraldSafe, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Automated Routines", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }

                    IconButton(
                        onClick = { showAddRoutineDialog = true },
                        modifier = Modifier.testTag("add_routine_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Routine", tint = CyanNeon)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (routines.isEmpty()) {
                    Text("No automation routines created yet.", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                } else {
                    routines.forEach { routine ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(routine.title, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("Trigger: ${routine.triggerType} (${routine.triggerCondition})", fontSize = 11.sp, color = IndigoNeon)
                                Text("Goal: ${routine.actionGoal}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = routine.isEnabled,
                                onCheckedChange = { viewModel.toggleRoutine(routine) }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Activity Timeline Section (Audit Trail)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Audited Activity Timeline", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Text("Secrets strictly redacted below the UI layer.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(modifier = Modifier.height(10.dp))

                activityLogs.take(15).forEach { log ->
                    val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(log.actionType, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyanNeon)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("[$time]", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                            }
                            Text(log.summary, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }

    // Dialog for adding memory
    if (showAddMemoryDialog) {
        var category by remember { mutableStateOf("FACT") }
        var key by remember { mutableStateOf("") }
        var content by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddMemoryDialog = false },
            title = { Text("Store New Memory") },
            text = {
                Column {
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it },
                        label = { Text("Key (e.g. coffee_pref)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("Information / Content") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (key.isNotBlank() && content.isNotBlank()) {
                        viewModel.saveMemory(category, key, content)
                        showAddMemoryDialog = false
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddMemoryDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Dialog for adding routine
    if (showAddRoutineDialog) {
        var title by remember { mutableStateOf("") }
        var trigger by remember { mutableStateOf("BATTERY_LOW") }
        var cond by remember { mutableStateOf("< 20%") }
        var goal by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddRoutineDialog = false },
            title = { Text("Create Routine") },
            text = {
                Column {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Routine Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = cond, onValueChange = { cond = it }, label = { Text("Condition (e.g. < 20%)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = goal, onValueChange = { goal = it }, label = { Text("Action Goal") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (title.isNotBlank() && goal.isNotBlank()) {
                        viewModel.addRoutine(title, trigger, cond, goal)
                        showAddRoutineDialog = false
                    }
                }) {
                    Text("Add Routine")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddRoutineDialog = false }) { Text("Cancel") }
            }
        )
    }
}
