package com.example.security

import com.example.data.model.UserProfile

enum class RiskLevel {
    L0_SAFE,
    L1_CONFIRMATION,
    L2_SENSITIVE,
    L3_CRITICAL
}

sealed class AuthCheckResult {
    data object Success : AuthCheckResult()
    data class VoiceCheckFailed(val reason: String = "Tu mera malik nahi h.") : AuthCheckResult()
    data class PinRequired(val message: String) : AuthCheckResult()
    data class PinFailed(val attemptsRemaining: Int) : AuthCheckResult()
    data class LockedOut(val lockoutMinutesRemaining: Long) : AuthCheckResult()
    data class CriticalRejected(val reason: String) : AuthCheckResult()
}

class SecurityEngine {

    companion object {
        const val MAX_PIN_ATTEMPTS = 5
        const val LOCKOUT_DURATION_MS = 5 * 60 * 1000L // 5 minutes
    }

    private var failedPinAttempts = 0
    private var lockoutTimestamp = 0L

    /**
     * Determines risk level from user intent and tool classification
     */
    fun classifyRisk(toolName: String, intent: String): RiskLevel {
        val lower = intent.lowercase()

        // Critical operations that touch credentials, payment, device reset
        if (lower.contains("upi") || lower.contains("payment") || lower.contains("bank pin") ||
            lower.contains("atm") || lower.contains("factory reset") || lower.contains("wipe") ||
            lower.contains("format device")
        ) {
            return RiskLevel.L3_CRITICAL
        }

        // Sensitive operations requiring Voice Verification + 6-digit PIN
        if (toolName in listOf("FileSandboxTool_Delete", "VaultTool", "SystemSettings_Modify", "Communication_SendSMS", "Communication_Call") ||
            lower.contains("delete file") || lower.contains("send sms") || lower.contains("call ") ||
            lower.contains("change pin") || lower.contains("credential") || lower.contains("password")
        ) {
            return RiskLevel.L2_SENSITIVE
        }

        // Operations needing confirmation
        if (toolName in listOf("AppLauncherTool", "WebSearchTool", "RoutineSchedulerTool", "FileSandboxTool_Write") ||
            lower.contains("open") || lower.contains("search") || lower.contains("schedule") || lower.contains("create file")
        ) {
            return RiskLevel.L1_CONFIRMATION
        }

        return RiskLevel.L0_SAFE
    }

    /**
     * Verifies speaker against enrolled profile signature.
     * If failed: User is rejected with "Tu mera malik nahi h."
     */
    fun verifySpeaker(
        profile: UserProfile?,
        inputSignature: String,
        simulatedFailTest: Boolean = false
    ): Boolean {
        if (simulatedFailTest) return false
        if (profile == null || !profile.isVoiceEnrolled) {
            // If voice is not yet enrolled, permit owner mode or require setup
            return true
        }
        val enrolled = profile.voiceSpeakerSignature.trim()
        if (enrolled.isEmpty()) return true

        // Compare acoustic parameters
        return inputSignature.isNotBlank() && enrolled.equals(inputSignature, ignoreCase = true)
    }

    /**
     * Checks rate-limiting and lockout for PIN verification
     */
    fun isLockedOut(): Boolean {
        if (lockoutTimestamp == 0L) return false
        val now = System.currentTimeMillis()
        if (now - lockoutTimestamp < LOCKOUT_DURATION_MS) {
            return true
        }
        // Lockout expired: reset
        lockoutTimestamp = 0L
        failedPinAttempts = 0
        return false
    }

    fun getLockoutMinutesRemaining(): Long {
        if (!isLockedOut()) return 0
        val remaining = LOCKOUT_DURATION_MS - (System.currentTimeMillis() - lockoutTimestamp)
        return (remaining / 60000).coerceAtLeast(1)
    }

    /**
     * Verifies 6-digit Bewakoof PIN
     */
    fun verifyPin(profile: UserProfile?, enteredPin: String): AuthCheckResult {
        if (isLockedOut()) {
            return AuthCheckResult.LockedOut(getLockoutMinutesRemaining())
        }

        if (profile == null || !profile.isPinSet || profile.pinHash.isEmpty()) {
            // PIN not set up yet
            return AuthCheckResult.Success
        }

        if (enteredPin.length != 6 || !enteredPin.all { it.isDigit() }) {
            failedPinAttempts++
            if (failedPinAttempts >= MAX_PIN_ATTEMPTS) {
                lockoutTimestamp = System.currentTimeMillis()
                return AuthCheckResult.LockedOut(5)
            }
            return AuthCheckResult.PinFailed(MAX_PIN_ATTEMPTS - failedPinAttempts)
        }

        val isValid = CryptoUtils.verifyHash(enteredPin, profile.pinSalt, profile.pinHash)
        if (isValid) {
            failedPinAttempts = 0
            lockoutTimestamp = 0L
            return AuthCheckResult.Success
        } else {
            failedPinAttempts++
            if (failedPinAttempts >= MAX_PIN_ATTEMPTS) {
                lockoutTimestamp = System.currentTimeMillis()
                return AuthCheckResult.LockedOut(5)
            }
            return AuthCheckResult.PinFailed(MAX_PIN_ATTEMPTS - failedPinAttempts)
        }
    }

    /**
     * Redacts sensitive secrets from log strings
     */
    fun sanitizeForLogging(text: String): String {
        return text
            .replace(Regex("""\b\d{6}\b"""), "[REDACTED_6DIGIT_PIN]")
            .replace(Regex("""\b\d{8}\b"""), "[REDACTED_RECOVERY_CODE]")
            .replace(Regex("""(?i)(password|secret|key|token)\s*[:=]\s*\S+"""), "$1=[REDACTED]")
    }
}
