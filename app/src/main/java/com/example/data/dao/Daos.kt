package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.model.ActivityLog
import com.example.data.model.AgentMemory
import com.example.data.model.AgentTask
import com.example.data.model.AIProviderConfig
import com.example.data.model.RecoveryCode
import com.example.data.model.RoutineItem
import com.example.data.model.TaskStep
import com.example.data.model.UserProfile
import com.example.data.model.VaultCredential
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    fun getUserProfileFlow(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    suspend fun getUserProfile(): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUserProfile(profile: UserProfile)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM agent_tasks ORDER BY createdAt DESC")
    fun getAllTasks(): Flow<List<AgentTask>>

    @Query("SELECT * FROM agent_tasks WHERE status NOT IN ('COMPLETED', 'FAILED', 'CANCELLED', 'ROLLED_BACK') ORDER BY createdAt DESC")
    fun getActiveTasks(): Flow<List<AgentTask>>

    @Query("SELECT * FROM agent_tasks WHERE id = :taskId LIMIT 1")
    suspend fun getTaskById(taskId: String): AgentTask?

    @Query("SELECT * FROM agent_tasks WHERE operationId = :operationId LIMIT 1")
    suspend fun getTaskByOperationId(operationId: String): AgentTask?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTask(task: AgentTask)

    @Update
    suspend fun updateTask(task: AgentTask)

    @Query("UPDATE agent_tasks SET status = :status, updatedAt = :timestamp WHERE id = :taskId")
    suspend fun updateTaskStatus(taskId: String, status: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE agent_tasks SET status = 'CANCELLED', updatedAt = :timestamp WHERE status NOT IN ('COMPLETED', 'FAILED', 'CANCELLED', 'ROLLED_BACK')")
    suspend fun emergencyCancelAllActiveTasks(timestamp: Long = System.currentTimeMillis()): Int

    @Delete
    suspend fun deleteTask(task: AgentTask)

    @Query("DELETE FROM agent_tasks")
    suspend fun clearAllTasks()
}

@Dao
interface TaskStepDao {
    @Query("SELECT * FROM task_steps WHERE taskId = :taskId ORDER BY stepIndex ASC")
    fun getStepsForTask(taskId: String): Flow<List<TaskStep>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSteps(steps: List<TaskStep>)

    @Update
    suspend fun updateStep(step: TaskStep)
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM agent_memories ORDER BY updatedAt DESC")
    fun getAllMemories(): Flow<List<AgentMemory>>

    @Query("SELECT * FROM agent_memories WHERE category = :category ORDER BY updatedAt DESC")
    fun getMemoriesByCategory(category: String): Flow<List<AgentMemory>>

    @Query("SELECT * FROM agent_memories WHERE memoryKey LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%'")
    fun searchMemories(query: String): Flow<List<AgentMemory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: AgentMemory)

    @Delete
    suspend fun deleteMemory(memory: AgentMemory)

    @Query("DELETE FROM agent_memories WHERE id = :id")
    suspend fun deleteMemoryById(id: Long)

    @Query("DELETE FROM agent_memories")
    suspend fun clearAllMemories()
}

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 100): Flow<List<ActivityLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ActivityLog)

    @Query("DELETE FROM activity_logs")
    suspend fun clearLogs()
}

@Dao
interface RecoveryDao {
    @Query("SELECT * FROM recovery_codes ORDER BY codeIndex ASC")
    fun getAllCodes(): Flow<List<RecoveryCode>>

    @Query("SELECT COUNT(*) FROM recovery_codes WHERE isUsed = 0")
    fun getUnusedCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCodes(codes: List<RecoveryCode>)

    @Query("SELECT * FROM recovery_codes WHERE codeHash = :hash AND isUsed = 0 LIMIT 1")
    suspend fun findValidCodeByHash(hash: String): RecoveryCode?

    @Query("UPDATE recovery_codes SET isUsed = 1, usedAt = :timestamp WHERE id = :id")
    suspend fun markCodeAsUsed(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM recovery_codes")
    suspend fun clearCodes()
}

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_credentials ORDER BY updatedAt DESC")
    fun getAllCredentials(): Flow<List<VaultCredential>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCredential(credential: VaultCredential)

    @Delete
    suspend fun deleteCredential(credential: VaultCredential)

    @Query("DELETE FROM vault_credentials WHERE id = :id")
    suspend fun deleteCredentialById(id: Long)
}

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY id DESC")
    fun getAllRoutines(): Flow<List<RoutineItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutine(routine: RoutineItem)

    @Update
    suspend fun updateRoutine(routine: RoutineItem)

    @Delete
    suspend fun deleteRoutine(routine: RoutineItem)

    @Query("UPDATE routines SET lastExecutedAt = :timestamp WHERE id = :id")
    suspend fun updateLastExecuted(id: Long, timestamp: Long = System.currentTimeMillis())
}

@Dao
interface ProviderDao {
    @Query("SELECT * FROM ai_providers ORDER BY displayName ASC")
    fun getAllProviders(): Flow<List<AIProviderConfig>>

    @Query("SELECT * FROM ai_providers WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultProvider(): AIProviderConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProviders(providers: List<AIProviderConfig>)

    @Transaction
    suspend fun setDefaultProvider(providerId: String) {
        clearDefaultFlag()
        setProviderAsDefault(providerId)
    }

    @Query("UPDATE ai_providers SET isDefault = 0")
    suspend fun clearDefaultFlag()

    @Query("UPDATE ai_providers SET isDefault = 1 WHERE providerId = :providerId")
    suspend fun setProviderAsDefault(providerId: String)
}
