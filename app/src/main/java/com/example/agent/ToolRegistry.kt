package com.example.agent

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.AlarmClock
import android.provider.Settings
import com.example.ai.GeminiClient
import com.example.security.RiskLevel
import java.io.File

sealed class ToolExecutionResult {
    data class Success(val message: String, val data: Map<String, Any> = emptyMap()) : ToolExecutionResult()
    data class Failure(val errorMessage: String) : ToolExecutionResult()
    data class RequiresConfirmation(val prompt: String, val toolName: String, val arguments: Map<String, String>) : ToolExecutionResult()
    data class Refused(val reason: String) : ToolExecutionResult()
}

interface AgentTool {
    val name: String
    val displayName: String
    val riskLevel: RiskLevel
    val requiresAuthentication: Boolean
    val requiresConfirmation: Boolean
    val description: String

    suspend fun execute(context: Context, arguments: Map<String, String>): ToolExecutionResult
    suspend fun verify(context: Context, arguments: Map<String, String>): Boolean
}

class AppLauncherTool : AgentTool {
    override val name = "AppLauncherTool"
    override val displayName = "Open Application"
    override val riskLevel = RiskLevel.L1_CONFIRMATION
    override val requiresAuthentication = false
    override val requiresConfirmation = false
    override val description = "Launches an installed Android app by alias or package name"

    private val commonPackageMap = mapOf(
        "camera" to "com.android.camera",
        "calculator" to "com.google.android.calculator",
        "clock" to "com.google.android.deskclock",
        "settings" to "com.android.settings",
        "browser" to "com.android.chrome",
        "chrome" to "com.android.chrome",
        "maps" to "com.google.android.apps.maps",
        "youtube" to "com.google.android.youtube",
        "whatsapp" to "com.whatsapp",
        "gallery" to "com.google.android.apps.photos",
        "photos" to "com.google.android.apps.photos",
        "calendar" to "com.google.android.calendar",
        "contacts" to "com.google.android.contacts",
        "gmail" to "com.google.android.gm"
    )

    override suspend fun execute(context: Context, arguments: Map<String, String>): ToolExecutionResult {
        val appQuery = arguments["appName"]?.lowercase()?.trim() ?: return ToolExecutionResult.Failure("App name is required")
        val pm = context.packageManager

        // 1. Direct alias check
        var pkg = commonPackageMap[appQuery]
        if (pkg != null) {
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return ToolExecutionResult.Success("Launched $appQuery ($pkg)")
            }
        }

        // 2. Query all installed apps with a Launcher Intent
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
        val matched = resolveInfos.firstOrNull { ri ->
            val label = ri.loadLabel(pm).toString().lowercase()
            label.contains(appQuery) || ri.activityInfo.packageName.contains(appQuery)
        }

        if (matched != null) {
            val launchIntent = pm.getLaunchIntentForPackage(matched.activityInfo.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return ToolExecutionResult.Success("Launched ${matched.loadLabel(pm)} successfully")
            }
        }

        // 3. Fallback for settings
        if (appQuery.contains("setting")) {
            val settingsIntent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(settingsIntent)
            return ToolExecutionResult.Success("Settings opened")
        }

        return ToolExecutionResult.Failure("Could not find installed application matching '$appQuery'")
    }

    override suspend fun verify(context: Context, arguments: Map<String, String>): Boolean = true
}

class TorchTool : AgentTool {
    override val name = "TorchTool"
    override val displayName = "Flashlight / Torch"
    override val riskLevel = RiskLevel.L0_SAFE
    override val requiresAuthentication = false
    override val requiresConfirmation = false
    override val description = "Turns device flashlight/torch on or off"

    override suspend fun execute(context: Context, arguments: Map<String, String>): ToolExecutionResult {
        val action = arguments["action"]?.lowercase() ?: "on"
        val enable = action != "off" && action != "band"

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return ToolExecutionResult.Failure("Camera service unavailable")

        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: "0"

            cameraManager.setTorchMode(cameraId, enable)
            ToolExecutionResult.Success("Flashlight turned ${if (enable) "ON" else "OFF"}")
        } catch (e: Exception) {
            ToolExecutionResult.Failure("Unable to control flashlight: ${e.message}")
        }
    }

    override suspend fun verify(context: Context, arguments: Map<String, String>): Boolean = true
}

class VolumeControlTool : AgentTool {
    override val name = "VolumeControlTool"
    override val displayName = "Volume & Sound Control"
    override val riskLevel = RiskLevel.L0_SAFE
    override val requiresAuthentication = false
    override val requiresConfirmation = false
    override val description = "Adjusts media or ringer volume or sets silent/vibrate mode"

    override suspend fun execute(context: Context, arguments: Map<String, String>): ToolExecutionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ToolExecutionResult.Failure("Audio service not available")

        val action = arguments["action"]?.lowercase() ?: "up"

        return try {
            when (action) {
                "up", "increase" -> {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                    ToolExecutionResult.Success("Volume increased")
                }
                "down", "decrease" -> {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                    ToolExecutionResult.Success("Volume decreased")
                }
                "mute" -> {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                    ToolExecutionResult.Success("Media volume muted")
                }
                "unmute" -> {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
                    ToolExecutionResult.Success("Media volume unmuted")
                }
                "vibrate" -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    ToolExecutionResult.Success("Ringer mode set to Vibrate")
                }
                "normal" -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    ToolExecutionResult.Success("Ringer mode set to Normal")
                }
                else -> ToolExecutionResult.Failure("Unknown volume action: $action")
            }
        } catch (e: Exception) {
            ToolExecutionResult.Failure("Volume adjustment failed: ${e.message}")
        }
    }

    override suspend fun verify(context: Context, arguments: Map<String, String>): Boolean = true
}

class AlarmTimerTool : AgentTool {
    override val name = "AlarmTimerTool"
    override val displayName = "Alarm & Timer"
    override val riskLevel = RiskLevel.L1_CONFIRMATION
    override val requiresAuthentication = false
    override val requiresConfirmation = false
    override val description = "Sets device system alarm or countdown timer"

    override suspend fun execute(context: Context, arguments: Map<String, String>): ToolExecutionResult {
        val type = arguments["type"]?.lowercase() ?: "alarm"
        val message = arguments["message"] ?: "Bewakoof Reminder"

        return try {
            if (type == "timer") {
                val seconds = arguments["seconds"]?.toIntOrNull() ?: 300 // default 5 mins
                val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                    putExtra(AlarmClock.EXTRA_MESSAGE, message)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ToolExecutionResult.Success("Timer set for ${seconds / 60} minute(s)")
            } else {
                val hour = arguments["hour"]?.toIntOrNull() ?: 7
                val minutes = arguments["minutes"]?.toIntOrNull() ?: 0
                val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, minutes)
                    putExtra(AlarmClock.EXTRA_MESSAGE, message)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                val amPm = if (hour >= 12) "PM" else "AM"
                val displayHour = if (hour % 12 == 0) 12 else hour % 12
                ToolExecutionResult.Success("Alarm set for %d:%02d %s".format(displayHour, minutes, amPm))
            }
        } catch (e: Exception) {
            ToolExecutionResult.Failure("Alarm/Timer setup error: ${e.message}")
        }
    }

    override suspend fun verify(context: Context, arguments: Map<String, String>): Boolean = true
}

class DeviceSettingsTool : AgentTool {
    override val name = "DeviceSettingsTool"
    override val displayName = "System Settings Navigator"
    override val riskLevel = RiskLevel.L0_SAFE
    override val requiresAuthentication = false
    override val requiresConfirmation = false
    override val description = "Navigates directly to Android system settings panels"

    override suspend fun execute(context: Context, arguments: Map<String, String>): ToolExecutionResult {
        val target = arguments["target"]?.lowercase() ?: "all"

        val action = when (target) {
            "wifi", "internet" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth", "bt" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display", "brightness" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound", "volume" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Intent.ACTION_POWER_USAGE_SUMMARY
            "assist", "assistant" -> Settings.ACTION_VOICE_INPUT_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }

        return try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolExecutionResult.Success("Opened $target settings panel")
        } catch (e: Exception) {
            ToolExecutionResult.Failure("Could not open settings: ${e.message}")
        }
    }

    override suspend fun verify(context: Context, arguments: Map<String, String>): Boolean = true
}

class SystemTelemetryTool : AgentTool {
    override val name = "SystemTelemetryTool"
    override val displayName = "Device Telemetry & Status"
    override val riskLevel = RiskLevel.L0_SAFE
    override val requiresAuthentication = false
    override val requiresConfirmation = false
    override val description = "Checks battery, free storage, and device status"

    override suspend fun execute(context: Context, arguments: Map<String, String>): ToolExecutionResult {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val isCharging = bm?.isCharging ?: false

        // Storage stats
        val stat = StatFs(Environment.getDataDirectory().path)
        val bytesAvailable = stat.availableBlocksLong * stat.blockSizeLong
        val freeGb = "%.2f".format(bytesAvailable / (1024.0 * 1024.0 * 1024.0))

        val summary = "Battery: $batteryPct% (${if (isCharging) "Charging" else "Discharging"}), Free Storage: ${freeGb} GB"
        return ToolExecutionResult.Success(
            message = summary,
            data = mapOf(
                "battery" to batteryPct,
                "isCharging" to isCharging,
                "freeStorageGb" to freeGb
            )
        )
    }

    override suspend fun verify(context: Context, arguments: Map<String, String>): Boolean = true
}

class FileSandboxTool : AgentTool {
    override val name = "FileSandboxTool"
    override val displayName = "Secure Sandboxed File Manager"
    override val riskLevel = RiskLevel.L2_SENSITIVE
    override val requiresAuthentication = true
    override val requiresConfirmation = true
    override val description = "Manages files safely inside app's sandboxed storage without path traversal"

    override suspend fun execute(context: Context, arguments: Map<String, String>): ToolExecutionResult {
        val action = arguments["action"]?.lowercase() ?: "list"
        val fileName = arguments["fileName"]?.trim() ?: "notes.txt"
        val content = arguments["content"] ?: ""

        // Defense in depth: Check for path traversal attacks
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return ToolExecutionResult.Refused("Security violation: Path traversal attempt rejected")
        }

        val sandboxDir = File(context.filesDir, "bewakoof_sandbox").apply { if (!exists()) mkdirs() }
        val targetFile = File(sandboxDir, fileName)

        return when (action) {
            "write", "create" -> {
                targetFile.writeText(content, Charsets.UTF_8)
                ToolExecutionResult.Success("File '$fileName' created/updated in secure sandbox (${targetFile.length()} bytes)")
            }
            "read" -> {
                if (!targetFile.exists()) {
                    ToolExecutionResult.Failure("File '$fileName' does not exist in sandbox")
                } else {
                    val text = targetFile.readText(Charsets.UTF_8)
                    ToolExecutionResult.Success("File '$fileName':\n$text")
                }
            }
            "delete" -> {
                if (!targetFile.exists()) {
                    ToolExecutionResult.Failure("File '$fileName' not found")
                } else {
                    targetFile.delete()
                    ToolExecutionResult.Success("File '$fileName' permanently deleted from sandbox")
                }
            }
            "list" -> {
                val files = sandboxDir.list() ?: emptyArray()
                val listStr = if (files.isEmpty()) "Sandbox is empty" else files.joinToString(", ")
                ToolExecutionResult.Success("Sandbox files: $listStr")
            }
            else -> ToolExecutionResult.Failure("Unsupported file operation: $action")
        }
    }

    override suspend fun verify(context: Context, arguments: Map<String, String>): Boolean {
        val action = arguments["action"]?.lowercase() ?: ""
        val fileName = arguments["fileName"]?.trim() ?: return false
        val sandboxDir = File(context.filesDir, "bewakoof_sandbox")
        val target = File(sandboxDir, fileName)

        return when (action) {
            "write", "create" -> target.exists() && target.length() > 0
            "delete" -> !target.exists()
            else -> true
        }
    }
}

class CommunicationHelperTool : AgentTool {
    override val name = "CommunicationHelperTool"
    override val displayName = "Communications & Messaging"
    override val riskLevel = RiskLevel.L2_SENSITIVE
    override val requiresAuthentication = true
    override val requiresConfirmation = true
    override val description = "Prepares SMS or phone call intents without unauthorized auto-sending"

    override suspend fun execute(context: Context, arguments: Map<String, String>): ToolExecutionResult {
        val type = arguments["type"]?.lowercase() ?: "sms"
        val recipient = arguments["recipient"] ?: ""
        val message = arguments["message"] ?: ""

        return when (type) {
            "sms" -> {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("smsto:$recipient")
                    putExtra("sms_body", message)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ToolExecutionResult.Success("Prepared SMS draft to $recipient with message: \"$message\"")
            }
            "call" -> {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$recipient")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ToolExecutionResult.Success("Dialer opened for $recipient")
            }
            else -> ToolExecutionResult.Failure("Unknown communication type: $type")
        }
    }

    override suspend fun verify(context: Context, arguments: Map<String, String>): Boolean = true
}

class WebSearchAgentTool : AgentTool {
    override val name = "WebSearchAgentTool"
    override val displayName = "Web Search & Browse"
    override val riskLevel = RiskLevel.L1_CONFIRMATION
    override val requiresAuthentication = false
    override val requiresConfirmation = false
    override val description = "Conducts web query or opens trusted URL"

    override suspend fun execute(context: Context, arguments: Map<String, String>): ToolExecutionResult {
        val query = arguments["query"]?.trim() ?: return ToolExecutionResult.Failure("Search query required")
        val uri = if (query.startsWith("http://") || query.startsWith("https://")) {
            Uri.parse(query)
        } else {
            Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
        }

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return ToolExecutionResult.Success("Browsing web search for '$query'")
    }

    override suspend fun verify(context: Context, arguments: Map<String, String>): Boolean = true
}

class GeneralKnowledgeAiTool : AgentTool {
    override val name = "GeneralKnowledgeAiTool"
    override val displayName = "Gemini Reasoning & Knowledge"
    override val riskLevel = RiskLevel.L0_SAFE
    override val requiresAuthentication = false
    override val requiresConfirmation = false
    override val description = "Answers open-ended knowledge, drafting, or complex reasoning questions using Gemini 3.5 Flash or local fallback"

    override suspend fun execute(context: Context, arguments: Map<String, String>): ToolExecutionResult {
        val prompt = arguments["prompt"]?.trim() ?: return ToolExecutionResult.Failure("Prompt is required")

        // Try real Gemini API if configured
        val geminiResult = GeminiClient.generateContent(prompt)
        return geminiResult.fold(
            onSuccess = { answer ->
                ToolExecutionResult.Success(answer)
            },
            onFailure = {
                // Graceful local edge response
                ToolExecutionResult.Success("Ji Malik: $prompt ke baare me local agent taiyar hai. Hardware aur task actions offline chal rahe hain.")
            }
        )
    }

    override suspend fun verify(context: Context, arguments: Map<String, String>): Boolean = true
}

class ToolRegistry {
    val tools: Map<String, AgentTool> = listOf(
        AppLauncherTool(),
        TorchTool(),
        VolumeControlTool(),
        AlarmTimerTool(),
        DeviceSettingsTool(),
        SystemTelemetryTool(),
        FileSandboxTool(),
        CommunicationHelperTool(),
        WebSearchAgentTool(),
        GeneralKnowledgeAiTool()
    ).associateBy { it.name }

    fun getTool(name: String): AgentTool? = tools[name]
}
