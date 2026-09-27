package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.agent.AiRouter
import com.example.agent.TaskEngine
import com.example.agent.TaskExecutionStatus
import com.example.agent.ToolRegistry
import com.example.data.db.BewakoofDatabase
import com.example.data.model.ActivityLog
import com.example.data.model.AgentMemory
import com.example.data.model.AgentTask
import com.example.data.model.AIProviderConfig
import com.example.data.model.RecoveryCode
import com.example.data.model.RoutineItem
import com.example.data.model.UserProfile
import com.example.data.model.VaultCredential
import com.example.data.repository.BewakoofRepository
import com.example.security.SecurityEngine
import com.example.service.BewakoofWakeService
import com.example.voice.AgentVoiceState
import com.example.voice.VoiceAcousticStats
import com.example.voice.VoiceDspEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BewakoofViewModel(application: Application) : AndroidViewModel(application) {

    private val database = BewakoofDatabase.getDatabase(application, viewModelScope)
    val repository = BewakoofRepository(database)
    val securityEngine = SecurityEngine()
    val toolRegistry = ToolRegistry()
    val aiRouter = AiRouter()

    val taskEngine = TaskEngine(
        repository = repository,
        securityEngine = securityEngine,
        toolRegistry = toolRegistry,
        aiRouter = aiRouter,
        scope = viewModelScope
    )

    private var voiceEngine: VoiceDspEngine? = null

    // UI Feedback state
    private val _agentReplyText = MutableStateFlow("Namaste Malik! Voice orb tap kijiye ya command boliye.")
    val agentReplyText: StateFlow<String> = _agentReplyText.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    private val _generatedRecoveryCodesList = MutableStateFlow<List<String>>(emptyList())
    val generatedRecoveryCodesList: StateFlow<List<String>> = _generatedRecoveryCodesList.asStateFlow()

    private val _exportedBackupPayload = MutableStateFlow<String?>(null)
    val exportedBackupPayload: StateFlow<String?> = _exportedBackupPayload.asStateFlow()

    // Decrypted credential cache
    private val _decryptedCredential = MutableStateFlow<Pair<Long, String>?>(null)
    val decryptedCredential: StateFlow<Pair<Long, String>?> = _decryptedCredential.asStateFlow()

    // Dialog state holders
    val showPinPromptDialog = MutableStateFlow(false)
    val pinPromptMessage = MutableStateFlow("")

    val showConfirmationDialog = MutableStateFlow(false)
    val confirmationPrompt = MutableStateFlow("")

    // Database flows with lifecycle stateIn
    val userProfile: StateFlow<UserProfile?> = repository.userProfile.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null
    )

    val allTasks: StateFlow<List<AgentTask>> = repository.allTasks.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val activeTasks: StateFlow<List<AgentTask>> = repository.activeTasks.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val recoveryCodes: StateFlow<List<RecoveryCode>> = repository.recoveryCodes.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val unusedRecoveryCodesCount: StateFlow<Int> = repository.unusedRecoveryCodesCount.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), 0
    )

    val allMemories: StateFlow<List<AgentMemory>> = repository.allMemories.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val recentActivity: StateFlow<List<ActivityLog>> = repository.recentActivity.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val allCredentials: StateFlow<List<VaultCredential>> = repository.allCredentials.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val allRoutines: StateFlow<List<RoutineItem>> = repository.allRoutines.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val allProviders: StateFlow<List<AIProviderConfig>> = repository.allProviders.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val taskStatus: StateFlow<TaskExecutionStatus> = taskEngine.currentStatus

    private val _voiceState = MutableStateFlow(AgentVoiceState.IDLE)
    val voiceState: StateFlow<AgentVoiceState> = _voiceState.asStateFlow()

    private val _acousticStats = MutableStateFlow(VoiceAcousticStats())
    val acousticStats: StateFlow<VoiceAcousticStats> = _acousticStats.asStateFlow()

    private val _activeAudioRoute = MutableStateFlow("Speaker")
    val activeAudioRoute: StateFlow<String> = _activeAudioRoute.asStateFlow()

    init {
        initVoiceEngine()
        observeTaskStatus()
    }

    private fun initVoiceEngine() {
        voiceEngine = VoiceDspEngine(
            context = getApplication(),
            onTranscriptRecognized = { transcript, isFinal ->
                if (isFinal) {
                    executeGoal(transcript)
                }
            },
            onError = { err ->
                _statusBannerMessage.value = err
            }
        )

        // Observe voice engine states
        viewModelScope.launch {
            voiceEngine?.voiceState?.collect { _voiceState.value = it }
        }
        viewModelScope.launch {
            voiceEngine?.acousticStats?.collect { _acousticStats.value = it }
        }
        viewModelScope.launch {
            voiceEngine?.activeAudioRoute?.collect { _activeAudioRoute.value = it }
        }
    }

    private fun observeTaskStatus() {
        viewModelScope.launch {
            taskEngine.currentStatus.collect { status ->
                when (status) {
                    is TaskExecutionStatus.WaitingForPin -> {
                        pinPromptMessage.value = status.message
                        showPinPromptDialog.value = true
                    }
                    is TaskExecutionStatus.WaitingForConfirmation -> {
                        confirmationPrompt.value = status.prompt
                        showConfirmationDialog.value = true
                    }
                    is TaskExecutionStatus.Completed -> {
                        _agentReplyText.value = status.resultText
                        voiceEngine?.speak(status.resultText)
                    }
                    is TaskExecutionStatus.Failed -> {
                        _agentReplyText.value = status.reason
                        voiceEngine?.speak(status.reason)
                    }
                    else -> {}
                }
            }
        }
    }

    fun toggleListening() {
        if (_voiceState.value == AgentVoiceState.LISTENING) {
            voiceEngine?.stopListening()
        } else {
            voiceEngine?.startListening()
        }
    }

    fun executeGoal(goal: String, simulateVoiceMismatch: Boolean = false) {
        val sig = voiceEngine?.computeSpeakerSignature("malik_voice_token") ?: "VOICE_SIG_OWNER"
        _agentReplyText.value = "Understanding: \"$goal\"..."
        taskEngine.processGoal(
            context = getApplication(),
            rawGoal = goal,
            simulatedSpeakerSignature = sig,
            simulatedVoiceFail = simulateVoiceMismatch,
            onOutputMessage = { msg, isError ->
                _agentReplyText.value = msg
                if (!isError) {
                    voiceEngine?.speak(msg)
                } else {
                    voiceEngine?.speak(msg)
                }
            }
        )
    }

    /**
     * Simulates unknown speaker attempting sensitive action.
     * Triggers the required response: "Tu mera malik nahi h."
     */
    fun testVoiceVerificationFailure() {
        executeGoal("Delete file notes.txt", simulateVoiceMismatch = true)
    }

    fun submitPin(enteredPin: String) {
        showPinPromptDialog.value = false
        taskEngine.submitPinForActiveTask(
            context = getApplication(),
            enteredPin = enteredPin,
            onOutputMessage = { msg, _ ->
                _agentReplyText.value = msg
                voiceEngine?.speak(msg)
            }
        )
    }

    fun confirmActiveTask(confirmed: Boolean) {
        showConfirmationDialog.value = false
        taskEngine.confirmActiveTask(
            context = getApplication(),
            confirmed = confirmed,
            onOutputMessage = { msg, _ ->
                _agentReplyText.value = msg
                voiceEngine?.speak(msg)
            }
        )
    }

    fun emergencyStopEverything() {
        voiceEngine?.emergencyHalt()
        taskEngine.emergencyStopAll()
        _agentReplyText.value = "EMERGENCY STOPPED. All actions cancelled."
    }

    fun setupPin(pin: String) {
        viewModelScope.launch {
            val success = repository.setupPin(pin)
            if (success) {
                _statusBannerMessage.value = "6-digit PIN securely configured."
            } else {
                _statusBannerMessage.value = "Invalid PIN format. Enter exactly 6 digits."
            }
        }
    }

    fun enrollVoiceProfile() {
        viewModelScope.launch {
            val sig = voiceEngine?.computeSpeakerSignature("malik_voice_token") ?: "VOICE_SIG_OWNER"
            repository.enrollVoice(sig)
            _statusBannerMessage.value = "Voice profile acoustic fingerprint enrolled."
        }
    }

    fun generateFreshRecoveryCodes() {
        viewModelScope.launch {
            val codes = repository.generateFreshRecoveryCodes()
            _generatedRecoveryCodesList.value = codes
        }
    }

    fun redeemRecoveryCode(rawCode: String) {
        viewModelScope.launch {
            val success = repository.redeemRecoveryCode(rawCode)
            _statusBannerMessage.value = if (success) {
                "Recovery code redeemed successfully!"
            } else {
                "Invalid or already used recovery code."
            }
        }
    }

    fun toggleEarpieceMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleEarpieceMode(enabled)
            voiceEngine?.setAudioRoute(enabled)
        }
    }

    fun setWakeWordMode(mode: String) {
        viewModelScope.launch {
            repository.setWakeWordMode(mode)
        }
    }

    fun saveMemory(category: String, key: String, content: String) {
        viewModelScope.launch {
            repository.saveMemory(category, key, content)
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            repository.deleteMemory(id)
        }
    }

    fun addRoutine(title: String, triggerType: String, condition: String, actionGoal: String) {
        viewModelScope.launch {
            repository.addRoutine(title, triggerType, condition, actionGoal)
        }
    }

    fun toggleRoutine(routine: RoutineItem) {
        viewModelScope.launch {
            repository.toggleRoutine(routine)
        }
    }

    fun deleteRoutine(routine: RoutineItem) {
        viewModelScope.launch {
            repository.deleteRoutine(routine)
        }
    }

    fun storeVaultCredential(title: String, service: String, secret: String, pin: String) {
        viewModelScope.launch {
            val success = repository.storeCredential(title, service, secret, pin)
            _statusBannerMessage.value = if (success) "Credential encrypted in AES-256 vault." else "Incorrect PIN or encryption error."
        }
    }

    fun retrieveVaultCredential(id: Long, pin: String) {
        viewModelScope.launch {
            val secret = repository.retrieveCredential(id, pin)
            if (secret != null) {
                _decryptedCredential.value = id to secret
            } else {
                _statusBannerMessage.value = "Decryption failed. Incorrect PIN."
            }
        }
    }

    fun deleteVaultCredential(id: Long) {
        viewModelScope.launch {
            repository.deleteCredential(id)
            if (_decryptedCredential.value?.first == id) {
                _decryptedCredential.value = null
            }
        }
    }

    fun exportEncryptedBackup(pin: String) {
        viewModelScope.launch {
            val res = repository.exportEncryptedBackup(pin)
            res.onSuccess { payload ->
                _exportedBackupPayload.value = payload
                _statusBannerMessage.value = "Encrypted backup ready for export."
            }.onFailure { err ->
                _statusBannerMessage.value = "Export failed: ${err.message}"
            }
        }
    }

    fun restoreEncryptedBackup(payload: String, pin: String) {
        viewModelScope.launch {
            val res = repository.restoreEncryptedBackup(payload, pin)
            res.onSuccess {
                _statusBannerMessage.value = "Backup successfully restored & verified."
            }.onFailure { err ->
                _statusBannerMessage.value = "Restore failed: ${err.message}"
            }
        }
    }

    fun setDefaultProvider(providerId: String) {
        viewModelScope.launch {
            repository.setDefaultProvider(providerId)
        }
    }

    val isBackgroundWakeRunning: StateFlow<Boolean> = BewakoofWakeService.isServiceRunning

    fun toggleBackgroundWake(enabled: Boolean) {
        if (enabled) {
            BewakoofWakeService.startService(getApplication())
            _statusBannerMessage.value = "Background Voice Guard started. Bewakoof will wake on voice even when app is closed."
        } else {
            BewakoofWakeService.stopService(getApplication())
            _statusBannerMessage.value = "Background Voice Guard stopped."
        }
    }

    fun openDefaultAssistantSettings() {
        try {
            val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            getApplication<Application>().startActivity(intent)
        } catch (e: Exception) {
            _statusBannerMessage.value = "Could not open settings: ${e.message}"
        }
    }

    fun clearBanner() {
        _statusBannerMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        voiceEngine?.release()
    }
}
