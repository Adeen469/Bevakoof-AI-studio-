package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agent.TaskExecutionStatus
import com.example.ui.components.EmergencyStopButton
import com.example.ui.components.VoiceOrb
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.IndigoNeon
import com.example.ui.viewmodel.BewakoofViewModel
import com.example.voice.AgentVoiceState

@Composable
fun HomeScreen(
    viewModel: BewakoofViewModel,
    modifier: Modifier = Modifier
) {
    val voiceState by viewModel.voiceState.collectAsState()
    val acousticStats by viewModel.acousticStats.collectAsState()
    val audioRoute by viewModel.activeAudioRoute.collectAsState()
    val profile by viewModel.userProfile.collectAsState()
    val agentReply by viewModel.agentReplyText.collectAsState()
    val taskStatus by viewModel.taskStatus.collectAsState()

    var textInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Bar: Identity & Emergency Stop Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "BEWAKOOF",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = CyanNeon,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = IndigoNeon.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "EDGE AI",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndigoNeon,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "Personal Voice Agent • Owner: ${profile?.ownerName ?: "Malik"}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Universal Emergency Stop Control
            EmergencyStopButton(
                onStopClick = { viewModel.emergencyStopEverything() }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Audio Route & Earpiece Switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (profile?.earpieceModeEnabled == true) Icons.Default.PhoneInTalk else Icons.Default.VolumeUp,
                    contentDescription = null,
                    tint = CyanNeon,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Audio Route: $audioRoute",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FilterChip(
                selected = profile?.earpieceModeEnabled == true,
                onClick = {
                    viewModel.toggleEarpieceMode(profile?.earpieceModeEnabled != true)
                },
                label = {
                    Text(
                        if (profile?.earpieceModeEnabled == true) "Earpiece (Call Mode)" else "Normal Speaker",
                        fontSize = 11.sp
                    )
                },
                modifier = Modifier.testTag("earpiece_toggle_chip")
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Center Voice Orb
        VoiceOrb(
            voiceState = voiceState,
            acousticStats = acousticStats,
            onClick = { viewModel.toggleListening() }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // State indicator text
        val stateLabel = when (voiceState) {
            AgentVoiceState.LISTENING -> "Listening to Malik..."
            AgentVoiceState.PROCESSING -> "Planning & verifying..."
            AgentVoiceState.SPEAKING -> "Responding..."
            AgentVoiceState.EMERGENCY_STOPPED -> "EMERGENCY HALTED"
            else -> "Tap Orb to Speak"
        }
        Text(
            text = stateLabel,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (voiceState == AgentVoiceState.EMERGENCY_STOPPED) MaterialTheme.colorScheme.error else CyanNeon
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Wake Word Mode Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Wake Word: ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("\"${profile?.wakeWord ?: "Bewakoof"}\"", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyanNeon)
            Spacer(modifier = Modifier.width(8.dp))
            listOf("SESSION", "EVERY_COMMAND", "PERSISTENT").forEach { mode ->
                FilterChip(
                    selected = (profile?.wakeWordMode ?: "SESSION") == mode,
                    onClick = { viewModel.setWakeWordMode(mode) },
                    label = { Text(mode.replace("_", " "), fontSize = 10.sp) },
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Live Agent Response Speech Box
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("agent_reply_box"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(EmeraldSafe)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AGENT RESPONSE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = agentReply,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Active Task Progress Status (if running)
        if (taskStatus is TaskExecutionStatus.InProgress) {
            val inProgress = taskStatus as TaskExecutionStatus.InProgress
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = IndigoNeon.copy(alpha = 0.15f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Task In Progress", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyanNeon)
                        Text(inProgress.stepInfo, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Suggestion Chips (Fewest clicks for the user!)
        Text(
            text = "QUICK GOALS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.Start),
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = false,
                onClick = { viewModel.executeGoal("Check phone battery and storage") },
                label = { Text("🔋 Battery & Storage") }
            )
            FilterChip(
                selected = false,
                onClick = { viewModel.executeGoal("Open Camera") },
                label = { Text("📷 Open Camera") }
            )
            FilterChip(
                selected = false,
                onClick = { viewModel.executeGoal("Open Settings") },
                label = { Text("⚙️ Open Settings") }
            )
            FilterChip(
                selected = false,
                onClick = { viewModel.testVoiceVerificationFailure() },
                label = { Text("🛡️ Test 'Tu mera malik nahi h.'") },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                    labelColor = MaterialTheme.colorScheme.error
                )
            )
            FilterChip(
                selected = false,
                onClick = { viewModel.executeGoal("List sandbox files") },
                label = { Text("📁 Sandbox Files") }
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Bottom Command Input Bar (Keyboard & Send)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = { Text("Type goal e.g. Open Camera...", fontSize = 14.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (textInput.isNotBlank()) {
                        viewModel.executeGoal(textInput)
                        textInput = ""
                    }
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanNeon,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("text_goal_input")
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    if (textInput.isNotBlank()) {
                        viewModel.executeGoal(textInput)
                        textInput = ""
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(CyanNeon, CircleShape)
                    .testTag("send_goal_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send goal",
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
