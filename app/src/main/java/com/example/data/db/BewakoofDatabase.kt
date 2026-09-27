package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.ActivityDao
import com.example.data.dao.MemoryDao
import com.example.data.dao.ProviderDao
import com.example.data.dao.RecoveryDao
import com.example.data.dao.RoutineDao
import com.example.data.dao.TaskDao
import com.example.data.dao.TaskStepDao
import com.example.data.dao.UserDao
import com.example.data.dao.VaultDao
import com.example.data.model.ActivityLog
import com.example.data.model.AgentMemory
import com.example.data.model.AgentTask
import com.example.data.model.AIProviderConfig
import com.example.data.model.RecoveryCode
import com.example.data.model.RoutineItem
import com.example.data.model.TaskStep
import com.example.data.model.UserProfile
import com.example.data.model.VaultCredential
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        UserProfile::class,
        AgentTask::class,
        TaskStep::class,
        AgentMemory::class,
        ActivityLog::class,
        RecoveryCode::class,
        VaultCredential::class,
        RoutineItem::class,
        AIProviderConfig::class
    ],
    version = 1,
    exportSchema = false
)
abstract class BewakoofDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun taskDao(): TaskDao
    abstract fun taskStepDao(): TaskStepDao
    abstract fun memoryDao(): MemoryDao
    abstract fun activityDao(): ActivityDao
    abstract fun recoveryDao(): RecoveryDao
    abstract fun vaultDao(): VaultDao
    abstract fun routineDao(): RoutineDao
    abstract fun providerDao(): ProviderDao

    companion object {
        @Volatile
        private var INSTANCE: BewakoofDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): BewakoofDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BewakoofDatabase::class.java,
                    "bewakoof_master.db"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(DatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialData(database)
                    }
                }
            }
        }

        private suspend fun populateInitialData(database: BewakoofDatabase) {
            // Seed user profile
            database.userDao().saveUserProfile(
                UserProfile(
                    id = 1,
                    ownerName = "Malik",
                    wakeWord = "Bewakoof",
                    wakeWordMode = "SESSION",
                    earpieceModeEnabled = false,
                    isSetupCompleted = false
                )
            )

            // Seed AI providers - Zero cost stack priority
            val defaultProviders = listOf(
                AIProviderConfig(
                    providerId = "LOCAL",
                    displayName = "Bewakoof Edge Neural Engine",
                    isEnabled = true,
                    isDefault = true,
                    modelName = "Edge DSP & Semantic Engine (Offline ₹0)",
                    isLocal = true
                ),
                AIProviderConfig(
                    providerId = "GEMINI",
                    displayName = "Google Gemini Free Tier",
                    isEnabled = true,
                    isDefault = false,
                    modelName = "gemini-2.0-flash",
                    isLocal = false
                ),
                AIProviderConfig(
                    providerId = "GROQ",
                    displayName = "Groq Llama 3 70B (Free Tier)",
                    isEnabled = true,
                    isDefault = false,
                    modelName = "llama-3.3-70b-versatile",
                    isLocal = false
                ),
                AIProviderConfig(
                    providerId = "OPENROUTER",
                    displayName = "OpenRouter Free Models",
                    isEnabled = true,
                    isDefault = false,
                    modelName = "meta-llama/llama-3-8b-instruct:free",
                    isLocal = false
                ),
                AIProviderConfig(
                    providerId = "CEREBRAS",
                    displayName = "Cerebras Ultra-Fast Free",
                    isEnabled = true,
                    isDefault = false,
                    modelName = "llama3.1-8b",
                    isLocal = false
                )
            )
            database.providerDao().insertProviders(defaultProviders)

            // Seed initial memory
            database.memoryDao().insertMemory(
                AgentMemory(
                    category = "PREFERENCE",
                    memoryKey = "language_preference",
                    content = "Hinglish / Hindi & English natural bilingual responses"
                )
            )
            database.memoryDao().insertMemory(
                AgentMemory(
                    category = "FACT",
                    memoryKey = "security_posture",
                    content = "Defense in depth below UI with voice verification & 6-digit PIN"
                )
            )

            // Seed initial sample routine
            database.routineDao().insertRoutine(
                RoutineItem(
                    title = "Battery Guardian",
                    triggerType = "BATTERY_LOW",
                    triggerCondition = "< 15%",
                    actionGoal = "Battery warning & alert Malik to conserve power",
                    isEnabled = true
                )
            )

            // Initial activity log
            database.activityDao().insertLog(
                ActivityLog(
                    actionType = "SYSTEM_INITIALIZE",
                    summary = "Bewakoof Personal AI Agent initialized with encrypted local database",
                    riskLevel = "L0_SAFE",
                    status = "SUCCESS"
                )
            )
        }
    }
}
