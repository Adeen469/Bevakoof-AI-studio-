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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AddVaultCredentialDialog
import com.example.ui.components.PinPromptDialog
import com.example.ui.components.RecoveryCodesViewerDialog
import com.example.ui.components.SetupPinDialog
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonCritical
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.IndigoNeon
import com.example.ui.viewmodel.BewakoofViewModel

@Composable
fun SecurityVaultScreen(
    viewModel: BewakoofViewModel,
    modifier: Modifier = Modifier
) {
    val profile by viewModel.userProfile.collectAsState()
    val unusedCodesCount by viewModel.unusedRecoveryCodesCount.collectAsState()
    val credentials by viewModel.allCredentials.collectAsState()
    val generatedCodes by viewModel.generatedRecoveryCodesList.collectAsState()
    val decryptedSecret by viewModel.decryptedCredential.collectAsState()

    var showSetupPinDialog by remember { mutableStateOf(false) }
    var showAddCredentialDialog by remember { mutableStateOf(false) }
    var showCodesViewerDialog by remember { mutableStateOf(false) }
    var decryptingCredentialId by remember { mutableStateOf<Long?>(null) }
    var redeemCodeInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "SECURITY & VAULT",
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            color = CyanNeon,
            letterSpacing = 1.sp
        )
        Text(
            text = "Authorization below the UI, voice verification & zero-trust credential vault.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 1. PIN & Voice Authentication Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Defense-In-Depth Authentication", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 6-digit PIN row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("6-Digit Bewakoof PIN", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (profile?.isPinSet == true) "Active (PBKDF2/SHA-256 Hashed)" else "Not set (Recommended)",
                            fontSize = 11.sp,
                            color = if (profile?.isPinSet == true) EmeraldSafe else AmberWarning
                        )
                    }
                    Button(
                        onClick = { showSetupPinDialog = true },
                        modifier = Modifier.testTag("configure_pin_button")
                    ) {
                        Text(if (profile?.isPinSet == true) "Change PIN" else "Set PIN", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Voice Verification profile row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Voice Speaker Biometrics", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (profile?.isVoiceEnrolled == true) "Enrolled (${profile?.voiceSpeakerSignature})" else "Pending Enrollment",
                            fontSize = 11.sp,
                            color = if (profile?.isVoiceEnrolled == true) EmeraldSafe else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = { viewModel.enrollVoiceProfile() },
                        modifier = Modifier.testTag("enroll_voice_button")
                    ) {
                        Text("Enroll Voice", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Button to simulate voice verification failure ("Tu mera malik nahi h.")
                Button(
                    onClick = { viewModel.testVoiceVerificationFailure() },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonCritical.copy(alpha = 0.2f), contentColor = CrimsonCritical),
                    modifier = Modifier.fillMaxWidth().testTag("simulate_voice_fail_button")
                ) {
                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Simulate Unknown Speaker ('Tu mera malik nahi h.')", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. 10 Single-Use 8-Digit Recovery Codes Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = EmeraldSafe, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Single-Use Recovery Codes", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "10 cryptographically random 8-digit codes for emergency recovery if you forget your PIN. Each code is invalidated upon first use.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Unused Codes: $unusedCodesCount / 10",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (unusedCodesCount > 0) EmeraldSafe else AmberWarning
                    )

                    Button(
                        onClick = {
                            viewModel.generateFreshRecoveryCodes()
                            showCodesViewerDialog = true
                        },
                        modifier = Modifier.testTag("generate_recovery_codes_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Generate 10 Codes", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Redeem a single use code
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = redeemCodeInput,
                        onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) redeemCodeInput = it },
                        placeholder = { Text("Enter 8-digit code to redeem", fontSize = 12.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (redeemCodeInput.length == 8) {
                                viewModel.redeemRecoveryCode(redeemCodeInput)
                                redeemCodeInput = ""
                            }
                        }
                    ) {
                        Text("Redeem", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Encrypted Credential Vault Card
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
                        Icon(Icons.Default.Lock, contentDescription = null, tint = IndigoNeon, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AES-256 Credential Vault", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }

                    IconButton(
                        onClick = { showAddCredentialDialog = true },
                        modifier = Modifier.testTag("add_credential_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Credential", tint = CyanNeon)
                    }
                }

                Text(
                    "Credentials encrypted at rest using AES-256-GCM. Never logged or exposed in prompts.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (credentials.isEmpty()) {
                    Text("No credentials saved yet.", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                } else {
                    credentials.forEach { cred ->
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
                                Text(cred.title, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(cred.serviceName, fontSize = 11.sp, color = IndigoNeon)

                                if (decryptedSecret?.first == cred.id) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Decrypted: ${decryptedSecret?.second}",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldSafe
                                    )
                                }
                            }

                            Row {
                                IconButton(
                                    onClick = { decryptingCredentialId = cred.id }
                                ) {
                                    Icon(Icons.Default.Visibility, contentDescription = "Unlock", tint = CyanNeon, modifier = Modifier.size(20.dp))
                                }
                                IconButton(
                                    onClick = { viewModel.deleteVaultCredential(cred.id) }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = CrimsonCritical, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    if (showSetupPinDialog) {
        SetupPinDialog(
            onDismiss = { showSetupPinDialog = false },
            onPinSet = { pin -> viewModel.setupPin(pin) }
        )
    }

    if (showCodesViewerDialog && generatedCodes.isNotEmpty()) {
        RecoveryCodesViewerDialog(
            codes = generatedCodes,
            onDismiss = { showCodesViewerDialog = false }
        )
    }

    if (showAddCredentialDialog) {
        AddVaultCredentialDialog(
            onDismiss = { showAddCredentialDialog = false },
            onSave = { title, service, secret, pin ->
                viewModel.storeVaultCredential(title, service, secret, pin)
            }
        )
    }

    decryptingCredentialId?.let { credId ->
        PinPromptDialog(
            message = "Enter 6-digit PIN to decrypt credential in memory:",
            onDismiss = { decryptingCredentialId = null },
            onSubmit = { pin ->
                viewModel.retrieveVaultCredential(credId, pin)
                decryptingCredentialId = null
            }
        )
    }
}
