package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
    val isBgWakeActive by viewModel.isBackgroundWakeRunning.collectAsState()

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

        Spacer(modifier = Modifier.height(14.dp))

        // Background Voice Wake (Always-On Listener) Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isBgWakeActive) IndigoNeon.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.Hearing,
                            contentDescription = null,
                            tint = if (isBgWakeActive) EmeraldSafe else CyanNeon,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Background Voice Wake",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = if (isBgWakeActive) EmeraldSafe.copy(alpha = 0.2f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = if (isBgWakeActive) "ACTIVE" else "OFF",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isBgWakeActive) EmeraldSafe else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (isBgWakeActive) "Listening for 'Bewakoof' even when app is closed." else "Enable to wake without opening app.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = isBgWakeActive,
                        onCheckedChange = { viewModel.toggleBackgroundWake(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = EmeraldSafe,
                            checkedTrackColor = EmeraldSafe.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.testTag("bg_wake_switch")
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Or set as phone's Default Assistant:",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    OutlinedButton(
                        onClick = { viewModel.openDefaultAssistantSettings() },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("open_assistant_settings_btn")
                    ) {
                        Icon(Icons.Default.SettingsVoice, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("System Assistant Setup", fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

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
                    text = "Audio: $audioRoute",
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
                        if (profile?.earpieceModeEnabled == true) "Earpiece Mode" else "Normal Speaker",
                        fontSize = 11.sp
                    )
                },
                modifier = Modifier.testTag("earpiece_toggle_chip")
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

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
            AgentVoiceState.PROCESSING -> "Planning & executing..."
            AgentVoiceState.SPEAKING -> "Responding..."
            AgentVoiceState.EMERGENCY_STOPPED -> "EMERGENCY HALTED"
            else -> "Tap Orb or Say 'Bewakoof'"
        }
        Text(
            text = stateLabel,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (voiceState == AgentVoiceState.EMERGENCY_STOPPED) MaterialTheme.colorScheme.error else CyanNeon
        )

        Spacer(modifier = Modifier.height(12.dp))

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
            Column(modifier = Modifier.padding(14.dp)) {
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
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = agentReply,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Active Task Progress Status (if running)
        if (taskStatus is TaskExecutionStatus.InProgress) {
            val inProgress = taskStatus as TaskExecutionStatus.InProgress
            Spacer(modifier = Modifier.height(10.dp))
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

        Spacer(modifier = Modifier.height(14.dp))

        // Quick Suggestion Chips (Fewest clicks for the user!)
        Text(
            text = "REAL DEVICE CAPABILITIES (VOICE OR TAP)",
            fontSize = 10.sp,
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
                onClick = { viewModel.executeGoal("Turn torch on") },
                label = { Text("🔦 Torch ON") }
            )
            FilterChip(
                selected = false,
                onClick = { viewModel.executeGoal("Turn torch off") },
                label = { Text("🔦 Torch OFF") }
            )
            FilterChip(
                selected = false,
                onClick = { viewModel.executeGoal("Increase volume") },
                label = { Text("🔊 Volume Up") }
            )
            FilterChip(
                selected = false,
                onClick = { viewModel.executeGoal("Set timer 5 minutes") },
                label = { Text("⏳ 5 Min Timer") }
            )
            FilterChip(
                selected = false,
                onClick = { viewModel.executeGoal("Set alarm for 7 am") },
                label = { Text("⏰ Alarm 7:00 AM") }
            )
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
                onClick = { viewModel.executeGoal("Open wifi settings") },
                label = { Text("📶 Wi-Fi Settings") }
            )
            FilterChip(
                selected = false,
                onClick = { viewModel.executeGoal("What is quantum computing and how does it work?") },
                label = { Text("🤖 Ask Gemini AI") }
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
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bottom Command Input Bar (Voice or Text Prompt)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = { Text("Voice or text prompt e.g. 'Torch on'...", fontSize = 13.sp) },
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
