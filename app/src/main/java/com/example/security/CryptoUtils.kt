package com.example.security

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {

    private val secureRandom = SecureRandom()
    private const val GCM_TAG_LENGTH = 128 // bits
    private const val IV_LENGTH = 12 // bytes for GCM

    fun generateSalt(length: Int = 16): String {
        val salt = ByteArray(length)
        secureRandom.nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    fun generateOperationId(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Hashes PIN or secret with salt using PBKDF2WithHmacSHA256
     */
    fun hashWithSalt(input: String, saltBase64: String): String {
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val spec = PBEKeySpec(input.toCharArray(), salt, 10000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    fun verifyHash(input: String, saltBase64: String, expectedHash: String): Boolean {
        val calculated = hashWithSalt(input, saltBase64)
        return MessageDigest.isEqual(
            calculated.toByteArray(Charsets.UTF_8),
            expectedHash.toByteArray(Charsets.UTF_8)
        )
    }

    /**
     * Generates 10 unique random 8-digit single-use recovery codes
     */
    fun generateRecoveryCodes(): List<String> {
        val codes = mutableSetOf<String>()
        while (codes.size < 10) {
            val num = 10000000 + secureRandom.nextInt(90000000)
            codes.add(num.toString())
        }
        return codes.toList()
    }

    /**
     * AES-256-GCM encryption with random IV
     */
    data class EncryptedResult(
        val ciphertextBase64: String,
        val ivBase64: String,
        val authTag: String
    )

    fun encryptAesGcm(plaintext: String, secretKeyBytes: ByteArray): EncryptedResult {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(IV_LENGTH)
        secureRandom.nextBytes(iv)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        val key = SecretKeySpec(secretKeyBytes, "AES")

        cipher.init(Cipher.ENCRYPT_MODE, key, spec)
        val cipherBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        return EncryptedResult(
            ciphertextBase64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
            authTag = "GCM-128"
        )
    }

    fun decryptAesGcm(ciphertextBase64: String, ivBase64: String, secretKeyBytes: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
        val ciphertext = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        val key = SecretKeySpec(secretKeyBytes, "AES")

        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        val plaintextBytes = cipher.doFinal(ciphertext)
        return String(plaintextBytes, Charsets.UTF_8)
    }

    /**
     * Computes HMAC-SHA256 checksum for backup integrity verification
     */
    fun computeHmacSha256(data: String, keyBytes: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(keyBytes, "HmacSHA256"))
        val hmacBytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hmacBytes, Base64.NO_WRAP)
    }

    /**
     * Derives a stable 32-byte AES key from user PIN and device salt
     */
    fun deriveKeyFromPin(pin: String, saltBase64: String): ByteArray {
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val spec = PBEKeySpec(pin.toCharArray(), salt, 12000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }
}
