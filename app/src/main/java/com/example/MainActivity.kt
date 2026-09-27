package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.ConfirmationDialog
import com.example.ui.components.PinPromptDialog
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MemoryScreen
import com.example.ui.screens.SecurityVaultScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TaskEngineScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.BewakoofViewModel

enum class BewakoofTab(val title: String, val icon: ImageVector, val tag: String) {
    CONSOLE("Console", Icons.Default.Mic, "tab_console"),
    TASKS("Tasks", Icons.Default.ListAlt, "tab_tasks"),
    SECURITY("Security", Icons.Default.Security, "tab_security"),
    MEMORY("Memory", Icons.Default.Bookmark, "tab_memory"),
    SETTINGS("Settings", Icons.Default.Settings, "tab_settings")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                BewakoofApp()
            }
        }
    }
}

@Composable
fun BewakoofApp(
    viewModel: BewakoofViewModel = viewModel()
) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(BewakoofTab.CONSOLE) }
    val snackbarHostState = remember { SnackbarHostState() }

    val statusBanner by viewModel.statusBannerMessage.collectAsState()
    val showPinPrompt by viewModel.showPinPromptDialog.collectAsState()
    val pinPromptMsg by viewModel.pinPromptMessage.collectAsState()
    val showConfirmPrompt by viewModel.showConfirmationDialog.collectAsState()
    val confirmPromptMsg by viewModel.confirmationPrompt.collectAsState()

    // Request audio permission for microphone
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(statusBanner) {
        statusBanner?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                BewakoofTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) },
                        modifier = Modifier.testTag(tab.tag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                BewakoofTab.CONSOLE -> HomeScreen(viewModel)
                BewakoofTab.TASKS -> TaskEngineScreen(viewModel)
                BewakoofTab.SECURITY -> SecurityVaultScreen(viewModel)
                BewakoofTab.MEMORY -> MemoryScreen(viewModel)
                BewakoofTab.SETTINGS -> SettingsScreen(viewModel)
            }
        }
    }

    // Global Security Verification Dialogs
    if (showPinPrompt) {
        PinPromptDialog(
            message = pinPromptMsg,
            onDismiss = { viewModel.showPinPromptDialog.value = false },
            onSubmit = { enteredPin -> viewModel.submitPin(enteredPin) }
        )
    }

    if (showConfirmPrompt) {
        ConfirmationDialog(
            prompt = confirmPromptMsg,
            onConfirm = { viewModel.confirmActiveTask(true) },
            onDismiss = { viewModel.confirmActiveTask(false) }
        )
    }
}
