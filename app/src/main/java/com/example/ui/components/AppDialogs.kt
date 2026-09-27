package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.IndigoNeon

@Composable
fun PinPromptDialog(
    message: String,
    onDismiss: () -> Unit,
    onSubmit: (pin: String) -> Unit
) {
    var pinText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = CyanNeon) },
        title = { Text("Security Verification", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(message, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = pinText,
                    onValueChange = {
                        if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                            pinText = it
                            errorText = null
                        }
                    },
                    label = { Text("6-Digit Bewakoof PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    isError = errorText != null,
                    supportingText = {
                        errorText?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pin_input_field")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pinText.length == 6) {
                        onSubmit(pinText)
                    } else {
                        errorText = "Enter exactly 6 digits"
                    }
                },
                modifier = Modifier.testTag("pin_submit_button")
            ) {
                Text("Authorize")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ConfirmationDialog(
    prompt: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Security, contentDescription = null, tint = EmeraldSafe) },
        title = { Text("Confirm Action", fontWeight = FontWeight.Bold) },
        text = { Text(prompt, fontSize = 14.sp) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.testTag("confirm_action_button")
            ) {
                Text("Confirm & Execute")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SetupPinDialog(
    onDismiss: () -> Unit,
    onPinSet: (pin: String) -> Unit
) {
    var pinText by remember { mutableStateOf("") }
    var confirmPinText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Key, contentDescription = null, tint = CyanNeon) },
        title = { Text("Configure 6-Digit PIN", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Enforce authorization for L2 sensitive operations below the UI layer.", fontSize = 13.sp)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = pinText,
                    onValueChange = {
                        if (it.length <= 6 && it.all { c -> c.isDigit() }) pinText = it
                    },
                    label = { Text("Enter 6-Digit PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("setup_pin_field_1")
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPinText,
                    onValueChange = {
                        if (it.length <= 6 && it.all { c -> c.isDigit() }) confirmPinText = it
                    },
                    label = { Text("Confirm 6-Digit PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    isError = errorText != null,
                    supportingText = { errorText?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth().testTag("setup_pin_field_2")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pinText.length != 6) {
                        errorText = "PIN must be 6 digits"
                    } else if (pinText != confirmPinText) {
                        errorText = "PINs do not match"
                    } else {
                        onPinSet(pinText)
                        onDismiss()
                    }
                },
                modifier = Modifier.testTag("save_pin_button")
            ) {
                Text("Save PIN")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun RecoveryCodesViewerDialog(
    codes: List<String>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("10 Single-Use Recovery Codes", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Keep these 10 codes in a safe place. Each code is 8 digits and can be used once to recover your system if your PIN is forgotten.",
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                codes.forEachIndexed { idx, code ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Code #${idx + 1}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            code,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = CyanNeon
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, modifier = Modifier.testTag("dismiss_recovery_dialog")) {
                Text("I Have Saved Them")
            }
        }
    )
}

@Composable
fun AddVaultCredentialDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, service: String, secret: String, pin: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var service by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Store Encrypted Credential", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Stored using AES-256-GCM. Unlocked only with your Bewakoof PIN.", fontSize = 12.sp)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title (e.g. GitHub Token)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = service,
                    onValueChange = { service = it },
                    label = { Text("Service (e.g. API Gateway)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = { Text("Secret Value") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pin = it },
                    label = { Text("Confirm with 6-Digit PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    isError = error != null,
                    supportingText = { error?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (title.isBlank() || secret.isBlank() || pin.length != 6) {
                    error = "All fields required (6-digit PIN)"
                } else {
                    onSave(title, service, secret, pin)
                    onDismiss()
                }
            }) {
                Text("Encrypt & Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
