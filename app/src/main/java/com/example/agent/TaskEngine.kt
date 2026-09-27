package com.example.agent

import android.content.Context
import com.example.data.model.AgentTask
import com.example.data.model.TaskStep
import com.example.data.repository.BewakoofRepository
import com.example.security.AuthCheckResult
import com.example.security.CryptoUtils
import com.example.security.RiskLevel
import com.example.security.SecurityEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

sealed class TaskExecutionStatus {
    data object Idle : TaskExecutionStatus()
    data class InProgress(val task: AgentTask, val stepInfo: String) : TaskExecutionStatus()
    data class WaitingForPin(val task: AgentTask, val message: String) : TaskExecutionStatus()
    data class WaitingForConfirmation(val task: AgentTask, val prompt: String) : TaskExecutionStatus()
    data class Completed(val task: AgentTask, val resultText: String) : TaskExecutionStatus()
    data class Failed(val task: AgentTask, val reason: String) : TaskExecutionStatus()
    data class Cancelled(val taskId: String, val reason: String) : TaskExecutionStatus()
}

class TaskEngine(
    private val repository: BewakoofRepository,
    private val securityEngine: SecurityEngine,
    private val toolRegistry: ToolRegistry,
    private val aiRouter: AiRouter,
    private val scope: CoroutineScope
) {
    private val _currentStatus = MutableStateFlow<TaskExecutionStatus>(TaskExecutionStatus.Idle)
    val currentStatus: StateFlow<TaskExecutionStatus> = _currentStatus.asStateFlow()

    private var activeTask: AgentTask? = null

    /**
     * Entry point: processes user goal from voice or text input
     */
    fun processGoal(
        context: Context,
        rawGoal: String,
        simulatedSpeakerSignature: String = "",
        simulatedVoiceFail: Boolean = false,
        onOutputMessage: (message: String, isError: Boolean) -> Unit
    ) {
        scope.launch {
            val planned = aiRouter.parseIntentLocally(rawGoal)

            // Handle Emergency Stop immediately
            if (planned.isEmergencyStop) {
                emergencyStopAll()
                onOutputMessage("Emergency stop executed. All tasks halted.", false)
                return@launch
            }

            // Direct companion replies
            if (planned.toolName == "LOCAL_REPLY") {
                val reply = planned.directReplyText ?: "Ji Malik, sun raha hu."
                onOutputMessage(reply, false)
                return@launch
            }

            // Create new task with stable UUID and unique operation ID to prevent duplicate executions
            val taskId = UUID.randomUUID().toString()
            val operationId = CryptoUtils.generateOperationId()
            val task = AgentTask(
                id = taskId,
                userGoal = planned.userGoal,
                planSummary = planned.planSummary,
                status = "PLANNING",
                riskLevel = planned.riskLevel.name,
                toolName = planned.toolName,
                toolArguments = planned.toolArguments.entries.joinToString(";") { "${it.key}=${it.value}" },
                operationId = operationId
            )

            val inserted = repository.createTask(task)
            if (!inserted) {
                onOutputMessage("Duplicate operation detected and ignored.", false)
                return@launch
            }

            activeTask = task
            _currentStatus.value = TaskExecutionStatus.InProgress(task, "Planning execution steps...")

            // Security authorization check below UI
            val profile = repository.getProfileSync()

            // 1. Critical actions (L3)
            if (planned.riskLevel == RiskLevel.L3_CRITICAL) {
                val failMsg = "Critical security refusal: Action violates device safety boundaries."
                val failedTask = task.copy(status = "FAILED", errorMessage = failMsg)
                repository.updateTask(failedTask)
                _currentStatus.value = TaskExecutionStatus.Failed(failedTask, failMsg)
                onOutputMessage(failMsg, true)
                return@launch
            }

            // 2. Sensitive actions (L2): require Voice verification & 6-digit PIN
            if (planned.riskLevel == RiskLevel.L2_SENSITIVE) {
                // Speaker verification
                val isSpeakerVerified = securityEngine.verifySpeaker(
                    profile = profile,
                    inputSignature = simulatedSpeakerSignature,
                    simulatedFailTest = simulatedVoiceFail
                )

                if (!isSpeakerVerified) {
                    val voiceFailMsg = "Tu mera malik nahi h."
                    val failedTask = task.copy(status = "FAILED", errorMessage = voiceFailMsg)
                    repository.updateTask(failedTask)
                    repository.logActivity("VOICE_AUTH_FAILURE", "Unauthorized voice command rejected: $rawGoal", "L2_SENSITIVE", "FAILED")
                    _currentStatus.value = TaskExecutionStatus.Failed(failedTask, voiceFailMsg)
                    onOutputMessage(voiceFailMsg, true)
                    return@launch
                }

                // If PIN is set, request PIN
                if (profile?.isPinSet == true) {
                    val updatedTask = task.copy(status = "WAITING_FOR_PERMISSION")
                    repository.updateTask(updatedTask)
                    _currentStatus.value = TaskExecutionStatus.WaitingForPin(
                        task = updatedTask,
                        message = "Sensitive action requires 6-digit Bewakoof PIN."
                    )
                    return@launch
                }
            }

            // 3. Confirmation actions (L1)
            if (planned.riskLevel == RiskLevel.L1_CONFIRMATION && planned.toolName == "FileSandboxTool" && planned.toolArguments["action"] == "delete") {
                val updatedTask = task.copy(status = "WAITING_FOR_CONFIRMATION")
                repository.updateTask(updatedTask)
                _currentStatus.value = TaskExecutionStatus.WaitingForConfirmation(
                    task = updatedTask,
                    prompt = "Confirm execution of: ${planned.planSummary}?"
                )
                return@launch
            }

            // Execute immediately if safe or authorized
            executeToolPlan(context, task, planned.toolName, planned.toolArguments, onOutputMessage)
        }
    }

    /**
     * Resumes execution after PIN verification
     */
    fun submitPinForActiveTask(
        context: Context,
        enteredPin: String,
        onOutputMessage: (message: String, isError: Boolean) -> Unit
    ) {
        val task = activeTask ?: return
        scope.launch {
            val profile = repository.getProfileSync()
            when (val auth = securityEngine.verifyPin(profile, enteredPin)) {
                is AuthCheckResult.Success -> {
                    val argsMap = parseArgs(task.toolArguments)
                    executeToolPlan(context, task, task.toolName, argsMap, onOutputMessage)
                }
                is AuthCheckResult.PinFailed -> {
                    val msg = "Incorrect PIN. ${auth.attemptsRemaining} attempt(s) remaining."
                    onOutputMessage(msg, true)
                }
                is AuthCheckResult.LockedOut -> {
                    val msg = "Too many failed attempts. Locked out for ${auth.lockoutMinutesRemaining} minute(s)."
                    val failedTask = task.copy(status = "FAILED", errorMessage = msg)
                    repository.updateTask(failedTask)
                    _currentStatus.value = TaskExecutionStatus.Failed(failedTask, msg)
                    onOutputMessage(msg, true)
                }
                else -> {
                    val msg = "Authentication failed."
                    onOutputMessage(msg, true)
                }
            }
        }
    }

    /**
     * Confirms a waiting L1 action
     */
    fun confirmActiveTask(
        context: Context,
        confirmed: Boolean,
        onOutputMessage: (message: String, isError: Boolean) -> Unit
    ) {
        val task = activeTask ?: return
        scope.launch {
            if (!confirmed) {
                val cancelled = task.copy(status = "CANCELLED", errorMessage = "Cancelled by Malik")
                repository.updateTask(cancelled)
                _currentStatus.value = TaskExecutionStatus.Cancelled(task.id, "Action cancelled")
                onOutputMessage("Action cancelled.", false)
                return@launch
            }
            val argsMap = parseArgs(task.toolArguments)
            executeToolPlan(context, task, task.toolName, argsMap, onOutputMessage)
        }
    }

    private suspend fun executeToolPlan(
        context: Context,
        task: AgentTask,
        toolName: String,
        arguments: Map<String, String>,
        onOutputMessage: (message: String, isError: Boolean) -> Unit
    ) = withContext(Dispatchers.IO) {
        val runningTask = task.copy(status = "EXECUTING")
        repository.updateTask(runningTask)
        _currentStatus.value = TaskExecutionStatus.InProgress(runningTask, "Executing $toolName...")

        val tool = toolRegistry.getTool(toolName)
        if (tool == null) {
            val err = "Tool '$toolName' is not registered"
            val failedTask = runningTask.copy(status = "FAILED", errorMessage = err)
            repository.updateTask(failedTask)
            _currentStatus.value = TaskExecutionStatus.Failed(failedTask, err)
            onOutputMessage(err, true)
            return@withContext
        }

        // Record step
        val step = TaskStep(
            taskId = task.id,
            stepIndex = 1,
            actionDescription = "Execute ${tool.displayName}",
            toolName = toolName,
            status = "EXECUTING",
            verificationStrategy = "Tool outcome check"
        )
        repository.saveTaskSteps(listOf(step))

        val result = tool.execute(context, arguments)
        when (result) {
            is ToolExecutionResult.Success -> {
                // Verification phase
                val isVerified = tool.verify(context, arguments)
                if (isVerified) {
                    val completed = runningTask.copy(
                        status = "COMPLETED",
                        resultSummary = result.message
                    )
                    repository.updateTask(completed)
                    repository.updateTaskStep(step.copy(status = "COMPLETED", verificationResult = "VERIFIED_OK"))
                    repository.logActivity(
                        actionType = toolName,
                        summary = result.message,
                        riskLevel = task.riskLevel,
                        status = "SUCCESS"
                    )
                    _currentStatus.value = TaskExecutionStatus.Completed(completed, result.message)
                    onOutputMessage(result.message, false)
                } else {
                    val unverified = runningTask.copy(
                        status = "FAILED",
                        errorMessage = "Action executed but verification could not confirm outcome."
                    )
                    repository.updateTask(unverified)
                    repository.updateTaskStep(step.copy(status = "FAILED", verificationResult = "UNVERIFIED"))
                    _currentStatus.value = TaskExecutionStatus.Failed(unverified, "Verification failed")
                    onOutputMessage("Could not verify action completion.", true)
                }
            }
            is ToolExecutionResult.Failure -> {
                val failed = runningTask.copy(status = "FAILED", errorMessage = result.errorMessage)
                repository.updateTask(failed)
                repository.updateTaskStep(step.copy(status = "FAILED", verificationResult = result.errorMessage))
                repository.logActivity(toolName, result.errorMessage, task.riskLevel, "FAILED")
                _currentStatus.value = TaskExecutionStatus.Failed(failed, result.errorMessage)
                onOutputMessage(result.errorMessage, true)
            }
            is ToolExecutionResult.Refused -> {
                val refused = runningTask.copy(status = "FAILED", errorMessage = result.reason)
                repository.updateTask(refused)
                _currentStatus.value = TaskExecutionStatus.Failed(refused, result.reason)
                onOutputMessage(result.reason, true)
            }
            else -> {}
        }
    }

    fun emergencyStopAll() {
        scope.launch {
            activeTask = null
            repository.emergencyStopAll()
            _currentStatus.value = TaskExecutionStatus.Cancelled("ALL", "Universal emergency stop triggered")
        }
    }

    private fun parseArgs(argString: String): Map<String, String> {
        if (argString.isBlank()) return emptyMap()
        return argString.split(";")
            .filter { it.contains("=") }
            .associate {
                val parts = it.split("=", limit = 2)
                parts[0] to parts[1]
            }
    }
}
