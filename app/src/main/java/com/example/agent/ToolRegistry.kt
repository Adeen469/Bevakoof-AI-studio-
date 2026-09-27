package com.example.agent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
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
        "whatsapp" to "com.whatsapp"
    )

    override suspend fun execute(context: Context, arguments: Map<String, String>): ToolExecutionResult {
        val appQuery = arguments["appName"]?.lowercase()?.trim() ?: return ToolExecutionResult.Failure("App name is required")
        val pm = context.packageManager

        // Check if query is alias
        var pkg = commonPackageMap[appQuery]
        if (pkg == null) {
            // Search installed applications for matching label
            val apps = pm.getInstalledApplications(0)
            val matched = apps.firstOrNull {
                it.packageName.contains(appQuery, ignoreCase = true) ||
                        pm.getApplicationLabel(it).toString().contains(appQuery, ignoreCase = true)
            }
            pkg = matched?.packageName
        }

        if (pkg != null) {
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return ToolExecutionResult.Success("App $appQuery ($pkg) launched successfully")
            }
        }

        // Fallback for settings or web
        if (appQuery.contains("setting")) {
            val settingsIntent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(settingsIntent)
            return ToolExecutionResult.Success("Settings opened")
        }

        return ToolExecutionResult.Failure("Could not find installed application matching '$appQuery'")
    }

    override suspend fun verify(context: Context, arguments: Map<String, String>): Boolean {
        return true
    }
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
        val fileName = arguments["fileName"]?.trim() ?: "note.txt"
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

class ToolRegistry {
    val tools: Map<String, AgentTool> = listOf(
        AppLauncherTool(),
        SystemTelemetryTool(),
        FileSandboxTool(),
        CommunicationHelperTool(),
        WebSearchAgentTool()
    ).associateBy { it.name }

    fun getTool(name: String): AgentTool? = tools[name]
}
