package com.example.agent

import com.example.security.RiskLevel
import org.json.JSONObject

data class PlannedAgentIntent(
    val toolName: String,
    val toolArguments: Map<String, String>,
    val userGoal: String,
    val planSummary: String,
    val riskLevel: RiskLevel,
    val directReplyText: String? = null,
    val isEmergencyStop: Boolean = false
)

class AiRouter {

    /**
     * Local Zero-Cost Semantic Engine:
     * Parses intent deterministically on-device without cloud dependencies.
     */
    fun parseIntentLocally(rawInput: String): PlannedAgentIntent {
        val trimmed = rawInput.trim()
        val lower = trimmed.lowercase()

        // 1. Universal Emergency Stop
        if (lower.contains("stop everything") || lower.contains("ruk jao") || lower.contains("emergency stop")) {
            return PlannedAgentIntent(
                toolName = "EMERGENCY_STOP",
                toolArguments = emptyMap(),
                userGoal = trimmed,
                planSummary = "Immediate emergency halt of all active tasks, audio pipelines, and background jobs.",
                riskLevel = RiskLevel.L3_CRITICAL,
                directReplyText = "Emergency stop executed. Sab kuch rok diya gaya hai, Malik.",
                isEmergencyStop = true
            )
        }

        // 2. Battery & System Telemetry
        if (lower.contains("battery") || lower.contains("storage") || lower.contains("phone status") ||
            lower.contains("charge") || lower.contains("phone ka haal")
        ) {
            return PlannedAgentIntent(
                toolName = "SystemTelemetryTool",
                toolArguments = emptyMap(),
                userGoal = trimmed,
                planSummary = "Query local hardware sensors for battery percentage, charging state, and storage.",
                riskLevel = RiskLevel.L0_SAFE
            )
        }

        // 3. App Launching ("Open Camera", "Calculator kholo", "Launch settings")
        val openMatch = Regex("""(?i)(?:open|launch|kholo|chalao)\s+([a-zA-Z0-9\s]+)""").find(lower)
        if (openMatch != null) {
            val appTarget = openMatch.groupValues[1].trim()
            return PlannedAgentIntent(
                toolName = "AppLauncherTool",
                toolArguments = mapOf("appName" to appTarget),
                userGoal = trimmed,
                planSummary = "Locate package for '$appTarget' and launch application.",
                riskLevel = RiskLevel.L1_CONFIRMATION
            )
        }

        // 4. File Sandbox Operations ("Create file notes.txt", "Read file", "Delete file", "List sandbox")
        if (lower.contains("sandbox") || lower.contains("file")) {
            val isDelete = lower.contains("delete") || lower.contains("hatao") || lower.contains("remove")
            val isRead = lower.contains("read") || lower.contains("padho") || lower.contains("show")
            val isList = lower.contains("list") || lower.contains("kya hai")

            val fileMatch = Regex("""([a-zA-Z0-9_\-]+\.[a-zA-Z0-9]+)""").find(trimmed)
            val fileName = fileMatch?.value ?: "notes.txt"

            val action = when {
                isDelete -> "delete"
                isRead -> "read"
                isList -> "list"
                else -> "write"
            }

            val risk = if (isDelete) RiskLevel.L2_SENSITIVE else RiskLevel.L1_CONFIRMATION
            val content = if (action == "write") {
                trimmed.substringAfter("with", "").ifBlank { "Sample content from Malik at ${System.currentTimeMillis()}" }
            } else ""

            return PlannedAgentIntent(
                toolName = "FileSandboxTool",
                toolArguments = mapOf(
                    "action" to action,
                    "fileName" to fileName,
                    "content" to content
                ),
                userGoal = trimmed,
                planSummary = "Perform secure sandbox operation '$action' on '$fileName'.",
                riskLevel = risk
            )
        }

        // 5. Communications (SMS / Call)
        if (lower.contains("message") || lower.contains("sms") || lower.contains("call") || lower.contains("phone")) {
            val isCall = lower.contains("call") || lower.contains("dial")
            val type = if (isCall) "call" else "sms"

            val numberMatch = Regex("""\b\d{10}\b""").find(trimmed)
            val recipient = numberMatch?.value ?: "9876543210"
            val msgBody = if (type == "sms") {
                trimmed.substringAfter("with", "").substringAfter("saying", "").ifBlank { "Namaste from Bewakoof AI" }
            } else ""

            return PlannedAgentIntent(
                toolName = "CommunicationHelperTool",
                toolArguments = mapOf(
                    "type" to type,
                    "recipient" to recipient,
                    "message" to msgBody
                ),
                userGoal = trimmed,
                planSummary = "Prepare draft $type intent for recipient $recipient without auto-sending.",
                riskLevel = RiskLevel.L2_SENSITIVE
            )
        }

        // 6. Web Search
        if (lower.contains("search") || lower.contains("google") || lower.contains("find on web") || lower.contains("browse")) {
            val query = trimmed.replace(Regex("""(?i)(?:search|google|find on web|browse)\s*"""), "").trim()
            return PlannedAgentIntent(
                toolName = "WebSearchAgentTool",
                toolArguments = mapOf("query" to query),
                userGoal = trimmed,
                planSummary = "Open secure web search for '$query'.",
                riskLevel = RiskLevel.L1_CONFIRMATION
            )
        }

        // 7. General conversational reply / memory
        return PlannedAgentIntent(
            toolName = "LOCAL_REPLY",
            toolArguments = emptyMap(),
            userGoal = trimmed,
            planSummary = "Generate local companion response with zero cost.",
            riskLevel = RiskLevel.L0_SAFE,
            directReplyText = "Hukum kijiye Malik, mai taiyar hu. Aap apps open kar sakte hain, battery check kar sakte hain, ya sandbox files manage kar sakte hain."
        )
    }
}
