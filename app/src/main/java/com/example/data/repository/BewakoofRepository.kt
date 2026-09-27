package com.example.data.repository

import com.example.data.db.BewakoofDatabase
import com.example.data.model.ActivityLog
import com.example.data.model.AgentMemory
import com.example.data.model.AgentTask
import com.example.data.model.AIProviderConfig
import com.example.data.model.RecoveryCode
import com.example.data.model.RoutineItem
import com.example.data.model.TaskStep
import com.example.data.model.UserProfile
import com.example.data.model.VaultCredential
import com.example.security.CryptoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class BewakoofRepository(private val database: BewakoofDatabase) {

    private val userDao = database.userDao()
    private val taskDao = database.taskDao()
    private val taskStepDao = database.taskStepDao()
    private val memoryDao = database.memoryDao()
    private val activityDao = database.activityDao()
    private val recoveryDao = database.recoveryDao()
    private val vaultDao = database.vaultDao()
    private val routineDao = database.routineDao()
    private val providerDao = database.providerDao()

    // --- User Profile & Security ---
    val userProfile: Flow<UserProfile?> = userDao.getUserProfileFlow()

    suspend fun getProfileSync(): UserProfile? = withContext(Dispatchers.IO) {
        userDao.getUserProfile()
    }

    suspend fun updateOwnerName(name: String) = withContext(Dispatchers.IO) {
        val current = userDao.getUserProfile() ?: UserProfile()
        userDao.saveUserProfile(current.copy(ownerName = name, isSetupCompleted = true))
    }

    suspend fun setupPin(newPin: String): Boolean = withContext(Dispatchers.IO) {
        if (newPin.length != 6 || !newPin.all { it.isDigit() }) return@withContext false
        val salt = CryptoUtils.generateSalt()
        val hash = CryptoUtils.hashWithSalt(newPin, salt)
        val current = userDao.getUserProfile() ?: UserProfile()
        userDao.saveUserProfile(
            current.copy(
                pinHash = hash,
                pinSalt = salt,
                isPinSet = true,
                isSetupCompleted = true
            )
        )
        logActivity("SECURITY_SETUP", "6-digit Bewakoof PIN configured", "L2_SENSITIVE", "SUCCESS")
        true
    }

    suspend fun enrollVoice(signature: String) = withContext(Dispatchers.IO) {
        val current = userDao.getUserProfile() ?: UserProfile()
        userDao.saveUserProfile(
            current.copy(
                voiceSpeakerSignature = signature,
                isVoiceEnrolled = true
            )
        )
        logActivity("VOICE_ENROLLMENT", "Speaker biometric acoustic signature enrolled", "L2_SENSITIVE", "SUCCESS")
    }

    suspend fun setWakeWordMode(mode: String) = withContext(Dispatchers.IO) {
        val current = userDao.getUserProfile() ?: UserProfile()
        userDao.saveUserProfile(current.copy(wakeWordMode = mode))
    }

    suspend fun toggleEarpieceMode(enabled: Boolean) = withContext(Dispatchers.IO) {
        val current = userDao.getUserProfile() ?: UserProfile()
        userDao.saveUserProfile(current.copy(earpieceModeEnabled = enabled))
    }

    // --- 10 Single-Use Recovery Codes ---
    val recoveryCodes: Flow<List<RecoveryCode>> = recoveryDao.getAllCodes()
    val unusedRecoveryCodesCount: Flow<Int> = recoveryDao.getUnusedCount()

    suspend fun generateFreshRecoveryCodes(): List<String> = withContext(Dispatchers.IO) {
        val rawCodes = CryptoUtils.generateRecoveryCodes()
        recoveryDao.clearCodes()
        val entities = rawCodes.mapIndexed { index, code ->
            val salt = CryptoUtils.generateSalt(12)
            val hash = CryptoUtils.hashWithSalt(code, salt)
            RecoveryCode(
                codeIndex = index + 1,
                codeHash = hash,
                codeSalt = salt,
                isUsed = false
            )
        }
        recoveryDao.insertCodes(entities)
        logActivity("RECOVERY_SETUP", "10 single-use 8-digit recovery codes generated", "L2_SENSITIVE", "SUCCESS")
        rawCodes
    }

    suspend fun redeemRecoveryCode(rawCode: String): Boolean = withContext(Dispatchers.IO) {
        val all = recoveryDao.getAllCodes().firstOrNull() ?: emptyList()
        for (codeEntity in all) {
            if (!codeEntity.isUsed) {
                if (CryptoUtils.verifyHash(rawCode, codeEntity.codeSalt, codeEntity.codeHash)) {
                    recoveryDao.markCodeAsUsed(codeEntity.id)
                    logActivity("RECOVERY_REDEEM", "Single-use recovery code #${codeEntity.codeIndex} redeemed", "L2_SENSITIVE", "SUCCESS")
                    return@withContext true
                }
            }
        }
        false
    }

    // --- Task Engine Persistence & Idempotency ---
    val allTasks: Flow<List<AgentTask>> = taskDao.getAllTasks()
    val activeTasks: Flow<List<AgentTask>> = taskDao.getActiveTasks()

    fun getStepsForTask(taskId: String): Flow<List<TaskStep>> = taskStepDao.getStepsForTask(taskId)

    suspend fun createTask(task: AgentTask): Boolean = withContext(Dispatchers.IO) {
        // Enforce idempotency: check if operationId already exists
        val existing = taskDao.getTaskByOperationId(task.operationId)
        if (existing != null) {
            return@withContext false // duplicate rejected
        }
        taskDao.insertTask(task)
        true
    }

    suspend fun updateTask(task: AgentTask) = withContext(Dispatchers.IO) {
        taskDao.updateTask(task.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateTaskStatus(taskId: String, status: String) = withContext(Dispatchers.IO) {
        taskDao.updateTaskStatus(taskId, status)
    }

    suspend fun saveTaskSteps(steps: List<TaskStep>) = withContext(Dispatchers.IO) {
        taskStepDao.insertSteps(steps)
    }

    suspend fun updateTaskStep(step: TaskStep) = withContext(Dispatchers.IO) {
        taskStepDao.updateStep(step)
    }

    suspend fun emergencyStopAll(): Int = withContext(Dispatchers.IO) {
        val count = taskDao.emergencyCancelAllActiveTasks()
        logActivity("EMERGENCY_STOP", "Universal stop triggered: cancelled $count active task(s)", "L3_CRITICAL", "COMPLETED")
        count
    }

    // --- Memory ---
    val allMemories: Flow<List<AgentMemory>> = memoryDao.getAllMemories()

    suspend fun saveMemory(category: String, key: String, content: String) = withContext(Dispatchers.IO) {
        val mem = AgentMemory(
            category = category,
            memoryKey = key,
            content = content,
            confidence = 1.0f
        )
        memoryDao.insertMemory(mem)
        logActivity("MEMORY_STORE", "Stored memory under $category: $key", "L0_SAFE", "SUCCESS")
    }

    suspend fun deleteMemory(id: Long) = withContext(Dispatchers.IO) {
        memoryDao.deleteMemoryById(id)
    }

    suspend fun clearAllMemories() = withContext(Dispatchers.IO) {
        memoryDao.clearAllMemories()
    }

    // --- Activity Timeline ---
    val recentActivity: Flow<List<ActivityLog>> = activityDao.getRecentLogs(100)

    suspend fun logActivity(actionType: String, summary: String, riskLevel: String, status: String) = withContext(Dispatchers.IO) {
        activityDao.insertLog(
            ActivityLog(
                actionType = actionType,
                summary = summary,
                riskLevel = riskLevel,
                status = status
            )
        )
    }

    // --- Credential Vault ---
    val allCredentials: Flow<List<VaultCredential>> = vaultDao.getAllCredentials()

    suspend fun storeCredential(title: String, service: String, secretValue: String, userPin: String): Boolean = withContext(Dispatchers.IO) {
        val profile = userDao.getUserProfile() ?: return@withContext false
        if (!profile.isPinSet) return@withContext false
        val keyBytes = CryptoUtils.deriveKeyFromPin(userPin, profile.pinSalt)
        val encrypted = CryptoUtils.encryptAesGcm(secretValue, keyBytes)
        vaultDao.insertCredential(
            VaultCredential(
                title = title,
                serviceName = service,
                encryptedPayload = encrypted.ciphertextBase64,
                iv = encrypted.ivBase64,
                authTag = encrypted.authTag
            )
        )
        logActivity("VAULT_STORE", "Stored encrypted credential for $service", "L2_SENSITIVE", "SUCCESS")
        true
    }

    suspend fun retrieveCredential(credentialId: Long, userPin: String): String? = withContext(Dispatchers.IO) {
        val profile = userDao.getUserProfile() ?: return@withContext null
        val keyBytes = CryptoUtils.deriveKeyFromPin(userPin, profile.pinSalt)
        val all = vaultDao.getAllCredentials().firstOrNull() ?: emptyList()
        val item = all.find { it.id == credentialId } ?: return@withContext null
        try {
            CryptoUtils.decryptAesGcm(item.encryptedPayload, item.iv, keyBytes)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun deleteCredential(id: Long) = withContext(Dispatchers.IO) {
        vaultDao.deleteCredentialById(id)
    }

    // --- Routines ---
    val allRoutines: Flow<List<RoutineItem>> = routineDao.getAllRoutines()

    suspend fun addRoutine(title: String, triggerType: String, condition: String, actionGoal: String) = withContext(Dispatchers.IO) {
        routineDao.insertRoutine(
            RoutineItem(
                title = title,
                triggerType = triggerType,
                triggerCondition = condition,
                actionGoal = actionGoal
            )
        )
    }

    suspend fun toggleRoutine(routine: RoutineItem) = withContext(Dispatchers.IO) {
        routineDao.updateRoutine(routine.copy(isEnabled = !routine.isEnabled))
    }

    suspend fun deleteRoutine(routine: RoutineItem) = withContext(Dispatchers.IO) {
        routineDao.deleteRoutine(routine)
    }

    // --- AI Providers ---
    val allProviders: Flow<List<AIProviderConfig>> = providerDao.getAllProviders()

    suspend fun setDefaultProvider(providerId: String) = withContext(Dispatchers.IO) {
        providerDao.setDefaultProvider(providerId)
        logActivity("AI_PROVIDER_SWITCH", "Default AI provider switched to $providerId", "L0_SAFE", "SUCCESS")
    }

    // --- Encrypted Backup Export & Restore ---
    suspend fun exportEncryptedBackup(userPin: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val profile = userDao.getUserProfile() ?: return@withContext Result.failure(Exception("Profile missing"))
            if (!profile.isPinSet) return@withContext Result.failure(Exception("PIN not set"))

            val memories = memoryDao.getAllMemories().firstOrNull() ?: emptyList()
            val routines = routineDao.getAllRoutines().firstOrNull() ?: emptyList()
            val providers = providerDao.getAllProviders().firstOrNull() ?: emptyList()

            val rootJson = JSONObject().apply {
                put("version", 1)
                put("createdAt", System.currentTimeMillis())
                put("ownerName", profile.ownerName)

                val memArray = JSONArray()
                memories.forEach { m ->
                    memArray.put(JSONObject().apply {
                        put("category", m.category)
                        put("key", m.memoryKey)
                        put("content", m.content)
                    })
                }
                put("memories", memArray)

                val routineArray = JSONArray()
                routines.forEach { r ->
                    routineArray.put(JSONObject().apply {
                        put("title", r.title)
                        put("triggerType", r.triggerType)
                        put("triggerCondition", r.triggerCondition)
                        put("actionGoal", r.actionGoal)
                        put("isEnabled", r.isEnabled)
                    })
                }
                put("routines", routineArray)

                val provArray = JSONArray()
                providers.forEach { p ->
                    provArray.put(JSONObject().apply {
                        put("providerId", p.providerId)
                        put("displayName", p.displayName)
                        put("isDefault", p.isDefault)
                    })
                }
                put("providers", provArray)
            }

            val keyBytes = CryptoUtils.deriveKeyFromPin(userPin, profile.pinSalt)
            val encrypted = CryptoUtils.encryptAesGcm(rootJson.toString(), keyBytes)
            val hmac = CryptoUtils.computeHmacSha256(encrypted.ciphertextBase64, keyBytes)

            val packageJson = JSONObject().apply {
                put("format", "BEWAKOOF_ENCRYPTED_BACKUP")
                put("version", 1)
                put("ciphertext", encrypted.ciphertextBase64)
                put("iv", encrypted.ivBase64)
                put("salt", profile.pinSalt)
                put("hmac", hmac)
            }

            logActivity("BACKUP_EXPORT", "Encrypted backup exported with HMAC-SHA256 verification", "L2_SENSITIVE", "SUCCESS")
            Result.success(packageJson.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreEncryptedBackup(backupJsonString: String, userPin: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val packageJson = JSONObject(backupJsonString)
            if (packageJson.optString("format") != "BEWAKOOF_ENCRYPTED_BACKUP") {
                return@withContext Result.failure(Exception("Invalid backup format"))
            }

            val salt = packageJson.getString("salt")
            val ciphertext = packageJson.getString("ciphertext")
            val iv = packageJson.getString("iv")
            val expectedHmac = packageJson.getString("hmac")

            val keyBytes = CryptoUtils.deriveKeyFromPin(userPin, salt)
            val calculatedHmac = CryptoUtils.computeHmacSha256(ciphertext, keyBytes)

            // Strict HMAC check before decrypting: prevents corrupt data restores
            if (expectedHmac != calculatedHmac) {
                return@withContext Result.failure(Exception("Integrity check failed: invalid PIN or corrupted backup"))
            }

            val plaintext = CryptoUtils.decryptAesGcm(ciphertext, iv, keyBytes)
            val rootJson = JSONObject(plaintext)

            // Restore memories
            val memArray = rootJson.optJSONArray("memories")
            if (memArray != null) {
                for (i in 0 until memArray.length()) {
                    val m = memArray.getJSONObject(i)
                    memoryDao.insertMemory(
                        AgentMemory(
                            category = m.getString("category"),
                            memoryKey = m.getString("key"),
                            content = m.getString("content")
                        )
                    )
                }
            }

            // Restore routines
            val routineArray = rootJson.optJSONArray("routines")
            if (routineArray != null) {
                for (i in 0 until routineArray.length()) {
                    val r = routineArray.getJSONObject(i)
                    routineDao.insertRoutine(
                        RoutineItem(
                            title = r.getString("title"),
                            triggerType = r.getString("triggerType"),
                            triggerCondition = r.getString("triggerCondition"),
                            actionGoal = r.getString("actionGoal"),
                            isEnabled = r.optBoolean("isEnabled", true)
                        )
                    )
                }
            }

            logActivity("BACKUP_RESTORE", "Encrypted backup successfully restored and verified", "L2_SENSITIVE", "SUCCESS")
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
