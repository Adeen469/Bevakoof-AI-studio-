package com.example.agent

import com.example.security.RiskLevel

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
     * Parses intent deterministically on-device with zero required cloud dependencies,
     * while seamlessly delegating complex Q&A queries to Gemini 3.5 Flash when configured.
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

        // 2. Flashlight / Torch ("Torch on", "Flashlight chalao", "Torch band kar do")
        if (lower.contains("torch") || lower.contains("flashlight") || lower.contains("light on") || lower.contains("light off") || lower.contains("light band")) {
            val isOff = lower.contains("off") || lower.contains("band") || lower.contains("bujhao")
            val action = if (isOff) "off" else "on"
            return PlannedAgentIntent(
                toolName = "TorchTool",
                toolArguments = mapOf("action" to action),
                userGoal = trimmed,
                planSummary = "Turn device flashlight $action.",
                riskLevel = RiskLevel.L0_SAFE
            )
        }

        // 3. Volume & Sound Control ("Volume badhao", "Increase volume", "Mute audio", "Vibrate mode")
        if (lower.contains("volume") || lower.contains("awaaz") || lower.contains("mute") || lower.contains("silent") || lower.contains("vibrate")) {
            val action = when {
                lower.contains("up") || lower.contains("increase") || lower.contains("badhao") || lower.contains("tez") -> "up"
                lower.contains("down") || lower.contains("decrease") || lower.contains("kam") -> "down"
                lower.contains("unmute") -> "unmute"
                lower.contains("mute") -> "mute"
                lower.contains("vibrate") -> "vibrate"
                lower.contains("normal") -> "normal"
                else -> "up"
            }
            return PlannedAgentIntent(
                toolName = "VolumeControlTool",
                toolArguments = mapOf("action" to action),
                userGoal = trimmed,
                planSummary = "Adjust sound/volume setting: $action.",
                riskLevel = RiskLevel.L0_SAFE
            )
        }

        // 4. Alarm & Countdown Timer ("Set alarm for 7 am", "Timer 5 minute ka lagao")
        if (lower.contains("alarm") || lower.contains("timer") || lower.contains("wake me up")) {
            val isTimer = lower.contains("timer")
            if (isTimer) {
                val numMatch = Regex("""\b(\d+)\s*(?:min|minute|sec|second)?""").find(lower)
                val mins = numMatch?.groupValues?.get(1)?.toIntOrNull() ?: 5
                val seconds = if (lower.contains("sec")) mins else mins * 60
                return PlannedAgentIntent(
                    toolName = "AlarmTimerTool",
                    toolArguments = mapOf("type" to "timer", "seconds" to seconds.toString(), "message" to "Bewakoof Timer"),
                    userGoal = trimmed,
                    planSummary = "Set countdown timer for $mins minute(s).",
                    riskLevel = RiskLevel.L1_CONFIRMATION
                )
            } else {
                val hourMatch = Regex("""\b(\d{1,2})(?::(\d{2}))?\s*(am|pm)?""").find(lower)
                var hour = hourMatch?.groupValues?.get(1)?.toIntOrNull() ?: 7
                val minutes = hourMatch?.groupValues?.get(2)?.toIntOrNull() ?: 0
                val amPm = hourMatch?.groupValues?.get(3)
                if (amPm == "pm" && hour < 12) hour += 12
                if (amPm == "am" && hour == 12) hour = 0
                return PlannedAgentIntent(
                    toolName = "AlarmTimerTool",
                    toolArguments = mapOf("type" to "alarm", "hour" to hour.toString(), "minutes" to minutes.toString(), "message" to "Bewakoof Alarm"),
                    userGoal = trimmed,
                    planSummary = "Set system alarm for $hour:$minutes.",
                    riskLevel = RiskLevel.L1_CONFIRMATION
                )
            }
        }

        // 5. System Settings Navigation ("Open wifi", "Open bluetooth", "Display settings")
        if (lower.contains("wifi") || lower.contains("wi-fi") || lower.contains("bluetooth") ||
            (lower.contains("setting") && (lower.contains("display") || lower.contains("sound") || lower.contains("battery") || lower.contains("assist")))
        ) {
            val target = when {
                lower.contains("wifi") || lower.contains("wi-fi") -> "wifi"
                lower.contains("bluetooth") -> "bluetooth"
                lower.contains("display") -> "display"
                lower.contains("sound") -> "sound"
                lower.contains("battery") -> "battery"
                lower.contains("assist") -> "assist"
                else -> "all"
            }
            return PlannedAgentIntent(
                toolName = "DeviceSettingsTool",
                toolArguments = mapOf("target" to target),
                userGoal = trimmed,
                planSummary = "Open $target device settings panel.",
                riskLevel = RiskLevel.L0_SAFE
            )
        }

        // 6. Battery & System Telemetry
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

        // 7. App Launching ("Open Camera", "Calculator kholo", "Launch WhatsApp")
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

        // 8. File Sandbox Operations ("Create file notes.txt", "Read file", "Delete file", "List sandbox")
        if (lower.contains("sandbox") || (lower.contains("file") && (lower.contains("notes") || lower.contains("txt") || lower.contains("create") || lower.contains("delete")))) {
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

        // 9. Communications (SMS / Call)
        if (lower.contains("message") || lower.contains("sms") || lower.contains("call ") || lower.contains("phone")) {
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

        // 10. Web Search
        if (lower.contains("search") || lower.contains("google ") || lower.contains("find on web") || lower.contains("browse")) {
            val query = trimmed.replace(Regex("""(?i)(?:search|google|find on web|browse)\s*"""), "").trim()
            return PlannedAgentIntent(
                toolName = "WebSearchAgentTool",
                toolArguments = mapOf("query" to query),
                userGoal = trimmed,
                planSummary = "Open secure web search for '$query'.",
                riskLevel = RiskLevel.L1_CONFIRMATION
            )
        }

        // 11. Complex Questions / Reasoning / Open Prompts -> GeneralKnowledgeAiTool (Gemini 3.5 Flash or Local Edge)
        if (lower.startsWith("what ") || lower.startsWith("why ") || lower.startsWith("how ") ||
            lower.startsWith("explain ") || lower.startsWith("write ") || lower.startsWith("tell ") ||
            lower.startsWith("kya ") || lower.startsWith("kyu ") || lower.length > 25
        ) {
            return PlannedAgentIntent(
                toolName = "GeneralKnowledgeAiTool",
                toolArguments = mapOf("prompt" to trimmed),
                userGoal = trimmed,
                planSummary = "Process intelligent reasoning/knowledge query via Gemini 3.5 Flash or Local Edge AI.",
                riskLevel = RiskLevel.L0_SAFE
            )
        }

        // 12. General conversational greeting
        return PlannedAgentIntent(
            toolName = "LOCAL_REPLY",
            toolArguments = emptyMap(),
            userGoal = trimmed,
            planSummary = "Generate local companion response with zero cost.",
            riskLevel = RiskLevel.L0_SAFE,
            directReplyText = "Hukum kijiye Malik, mai taiyar hu. Aap apps open kar sakte hain, torch on/off kar sakte hain, alarm/timer laga sakte hain, volume adjust kar sakte hain, ya koi bhi sawal pooch sakte hain."
        )
    }
}
