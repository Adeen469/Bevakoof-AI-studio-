package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.IndigoNeon
import com.example.ui.viewmodel.BewakoofViewModel

@Composable
fun SettingsScreen(
    viewModel: BewakoofViewModel,
    modifier: Modifier = Modifier
) {
    val providers by viewModel.allProviders.collectAsState()
    val profile by viewModel.userProfile.collectAsState()
    val exportedPayload by viewModel.exportedBackupPayload.collectAsState()

    var showExportDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "SETTINGS & BACKUP",
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            color = CyanNeon,
            letterSpacing = 1.sp
        )
        Text(
            text = "AI provider registry, encrypted portable backup, and audio hardware setup.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 1. AI Provider Pool Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Hub, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI Provider Pool (Zero Mandatory Cost)", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }

                Text(
                    "Default is local edge engine (₹0, offline). Free-tier cloud providers are supported as optional fallbacks.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                providers.forEach { provider ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(provider.displayName, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                if (provider.isLocal) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(color = EmeraldSafe.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                        Text("OFFLINE ₹0", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = EmeraldSafe, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                    }
                                }
                            }
                            Text(provider.modelName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        RadioButton(
                            selected = provider.isDefault,
                            onClick = { viewModel.setDefaultProvider(provider.providerId) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Encrypted Backup & Restore Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = EmeraldSafe, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Encrypted Backup & Recovery", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }

                Text(
                    "AES-256-GCM encrypted backup archive with HMAC-SHA256 integrity tag. Atomic restore prevents corrupted states.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { showExportDialog = true },
                        modifier = Modifier.weight(1f).testTag("export_backup_button")
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export .bak", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { showRestoreDialog = true },
                        modifier = Modifier.weight(1f).testTag("restore_backup_button")
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restore .bak", fontSize = 12.sp)
                    }
                }

                if (exportedPayload != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Backup Encrypted Output:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyanNeon)
                    OutlinedTextField(
                        value = exportedPayload ?: "",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().height(100.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Audio & Earpiece Settings
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PhoneInTalk, contentDescription = null, tint = IndigoNeon, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Audio Routing & Discreet Call Mode", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Earpiece Mode", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Route voice agent audio to phone earpiece for private phone-call style conversation.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = profile?.earpieceModeEnabled == true,
                        onCheckedChange = { viewModel.toggleEarpieceMode(it) }
                    )
                }
            }
        }
    }

    // Export Dialog
    if (showExportDialog) {
        var pin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Encrypted Backup") },
            text = {
                Column {
                    Text("Enter your 6-digit PIN to derive the AES-256 encryption key:", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pin = it },
                        label = { Text("6-Digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (pin.length == 6) {
                        viewModel.exportEncryptedBackup(pin)
                        showExportDialog = false
                    }
                }) {
                    Text("Export")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showExportDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Restore Dialog
    if (showRestoreDialog) {
        var pin by remember { mutableStateOf("") }
        var payload by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("Restore Encrypted Backup") },
            text = {
                Column {
                    Text("Paste your encrypted backup JSON and enter the original 6-digit PIN. The HMAC-SHA256 signature is verified before restore.", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = payload,
                        onValueChange = { payload = it },
                        label = { Text("Backup Payload JSON") },
                        modifier = Modifier.fillMaxWidth().height(100.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pin = it },
                        label = { Text("6-Digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (payload.isNotBlank() && pin.length == 6) {
                        viewModel.restoreEncryptedBackup(payload, pin)
                        showRestoreDialog = false
                    }
                }) {
                    Text("Verify & Restore")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRestoreDialog = false }) { Text("Cancel") }
            }
        )
    }
}
