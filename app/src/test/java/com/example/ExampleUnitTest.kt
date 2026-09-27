package com.example

import com.example.agent.AiRouter
import com.example.data.model.UserProfile
import com.example.security.AuthCheckResult
import com.example.security.CryptoUtils
import com.example.security.RiskLevel
import com.example.security.SecurityEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testPinHashingAndVerification() {
        val pin = "123456"
        val salt = CryptoUtils.generateSalt()
        val hash = CryptoUtils.hashWithSalt(pin, salt)

        assertTrue(CryptoUtils.verifyHash("123456", salt, hash))
        assertFalse(CryptoUtils.verifyHash("654321", salt, hash))
    }

    @Test
    fun testRecoveryCodeGeneration() {
        val codes = CryptoUtils.generateRecoveryCodes()
        assertEquals(10, codes.size)
        // Verify all 10 are unique and 8 digits
        val uniqueSet = codes.toSet()
        assertEquals(10, uniqueSet.size)
        codes.forEach { code ->
            assertEquals(8, code.length)
            assertTrue(code.all { it.isDigit() })
        }
    }

    @Test
    fun testAesGcmEncryptionDecryption() {
        val key = ByteArray(32) { 7 }
        val secret = "SecretCredentialToken_98765"
        val encrypted = CryptoUtils.encryptAesGcm(secret, key)

        assertNotEquals(secret, encrypted.ciphertextBase64)
        val decrypted = CryptoUtils.decryptAesGcm(encrypted.ciphertextBase64, encrypted.ivBase64, key)
        assertEquals(secret, decrypted)
    }

    @Test
    fun testHmacIntegrity() {
        val key = ByteArray(32) { 3 }
        val payload = "{\"backup\": true}"
        val hmac = CryptoUtils.computeHmacSha256(payload, key)

        val hmacVerify = CryptoUtils.computeHmacSha256(payload, key)
        assertEquals(hmac, hmacVerify)

        val tamperedHmac = CryptoUtils.computeHmacSha256(payload + "tampered", key)
        assertNotEquals(hmac, tamperedHmac)
    }

    @Test
    fun testSecurityEngineRiskClassification() {
        val engine = SecurityEngine()
        assertEquals(RiskLevel.L0_SAFE, engine.classifyRisk("SystemTelemetryTool", "battery level"))
        assertEquals(RiskLevel.L1_CONFIRMATION, engine.classifyRisk("AppLauncherTool", "open camera"))
        assertEquals(RiskLevel.L2_SENSITIVE, engine.classifyRisk("FileSandboxTool_Delete", "delete file notes.txt"))
        assertEquals(RiskLevel.L3_CRITICAL, engine.classifyRisk("SystemSettings", "factory reset upi payment"))
    }

    @Test
    fun testSpeakerVerificationFailureTuMeraMalikNahiH() {
        val engine = SecurityEngine()
        val profile = UserProfile(
            isVoiceEnrolled = true,
            voiceSpeakerSignature = "VOICE_SIG_OWNER"
        )

        // Mismatched speaker signature
        val isVerified = engine.verifySpeaker(profile, "VOICE_SIG_INTRUDER")
        assertFalse(isVerified)

        // Matched speaker signature
        val isOwnerVerified = engine.verifySpeaker(profile, "VOICE_SIG_OWNER")
        assertTrue(isOwnerVerified)
    }

    @Test
    fun testPinRateLimitingAndLockout() {
        val engine = SecurityEngine()
        val salt = CryptoUtils.generateSalt()
        val hash = CryptoUtils.hashWithSalt("654321", salt)
        val profile = UserProfile(pinHash = hash, pinSalt = salt, isPinSet = true)

        // 4 failed attempts
        repeat(4) {
            val res = engine.verifyPin(profile, "000000")
            assertTrue(res is AuthCheckResult.PinFailed)
        }

        // 5th attempt triggers lockout
        val lockedRes = engine.verifyPin(profile, "000000")
        assertTrue(lockedRes is AuthCheckResult.LockedOut)
        assertTrue(engine.isLockedOut())
    }

    @Test
    fun testAiRouterDeterministicIntent() {
        val router = AiRouter()

        val emergency = router.parseIntentLocally("Bewakoof stop everything right now")
        assertTrue(emergency.isEmergencyStop)

        val battery = router.parseIntentLocally("Battery kitni bachi hai?")
        assertEquals("SystemTelemetryTool", battery.toolName)

        val camera = router.parseIntentLocally("Open camera please")
        assertEquals("AppLauncherTool", camera.toolName)
        assertEquals("camera", camera.toolArguments["appName"])
    }
}
