package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,
    val ownerName: String = "Malik",
    val pinHash: String = "",
    val pinSalt: String = "",
    val isPinSet: Boolean = false,
    val isVoiceEnrolled: Boolean = false,
    val voiceSpeakerSignature: String = "", // Acoustic fingerprint (pitch & spectral centroid stats)
    val wakeWord: String = "Bewakoof",
    val wakeWordMode: String = "SESSION", // EVERY_COMMAND, SESSION, PERSISTENT
    val earpieceModeEnabled: Boolean = false,
    val isSetupCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "agent_tasks",
    indices = [Index(value = ["operationId"], unique = true)]
)
data class AgentTask(
    @PrimaryKey val id: String, // Stable UUID
    val userGoal: String,
    val planSummary: String,
    val status: String, // CREATED, PLANNING, WAITING_FOR_PERMISSION, WAITING_FOR_CONFIRMATION, EXECUTING, BACKGROUND_EXECUTING, VERIFYING, COMPLETED, FAILED, PAUSED, CANCELLED, ROLLBACK_PENDING, ROLLED_BACK
    val riskLevel: String, // L0_SAFE, L1_CONFIRMATION, L2_SENSITIVE, L3_CRITICAL
    val toolName: String = "",
    val toolArguments: String = "",
    val resultSummary: String = "",
    val errorMessage: String = "",
    val operationId: String, // Enforces duplicate prevention
    val isBackground: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "task_steps",
    foreignKeys = [
        ForeignKey(
            entity = AgentTask::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["taskId"])]
)
data class TaskStep(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: String,
    val stepIndex: Int,
    val actionDescription: String,
    val toolName: String,
    val status: String, // PENDING, EXECUTING, VERIFYING, COMPLETED, FAILED
    val verificationStrategy: String,
    val verificationResult: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "agent_memories")
data class AgentMemory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String, // PREFERENCE, FACT, ALIAS, ROUTINE
    val memoryKey: String,
    val content: String,
    val confidence: Float = 1.0f,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "activity_logs")
data class ActivityLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actionType: String,
    val summary: String,
    val riskLevel: String,
    val status: String,
    val isSecretRedacted: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "recovery_codes",
    indices = [Index(value = ["codeHash"], unique = true)]
)
data class RecoveryCode(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val codeIndex: Int,
    val codeHash: String, // PBKDF2/SHA-256 hash of single-use code
    val codeSalt: String,
    val isUsed: Boolean = false,
    val usedAt: Long? = null
)

@Entity(tableName = "vault_credentials")
data class VaultCredential(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val serviceName: String,
    val encryptedPayload: String, // AES-256-GCM ciphertext in base64
    val iv: String,
    val authTag: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "routines")
data class RoutineItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val triggerType: String, // TIME, BATTERY_LOW, NETWORK_CHANGE, MANUAL
    val triggerCondition: String, // e.g. "08:00", "battery < 15"
    val actionGoal: String,
    val isEnabled: Boolean = true,
    val lastExecutedAt: Long? = null
)

@Entity(tableName = "ai_providers")
data class AIProviderConfig(
    @PrimaryKey val providerId: String, // LOCAL, GEMINI, GROQ, OPENROUTER, CEREBRAS
    val displayName: String,
    val isEnabled: Boolean = true,
    val isDefault: Boolean = false,
    val apiKeyMasked: String = "", // Stored encrypted or blank for local
    val endpointUrl: String = "",
    val modelName: String = "",
    val isLocal: Boolean = false
)
