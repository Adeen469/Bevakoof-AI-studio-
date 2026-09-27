package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.agent.AiRouter
import com.example.agent.TaskEngine
import com.example.agent.ToolRegistry
import com.example.data.db.BewakoofDatabase
import com.example.data.repository.BewakoofRepository
import com.example.security.SecurityEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class BewakoofWakeService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var wakeLock: PowerManager.WakeLock? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isListeningLoopActive = false
    private var isTtsReady = false

    private lateinit var repository: BewakoofRepository
    private lateinit var securityEngine: SecurityEngine
    private lateinit var toolRegistry: ToolRegistry
    private lateinit var aiRouter: AiRouter
    private lateinit var taskEngine: TaskEngine

    companion object {
        const val CHANNEL_ID = "bewakoof_voice_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.service.ACTION_START"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"
        const val ACTION_EMERGENCY_STOP = "com.example.service.ACTION_EMERGENCY_STOP"

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        fun startService(context: Context) {
            val intent = Intent(context, BewakoofWakeService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, BewakoofWakeService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val database = BewakoofDatabase.getDatabase(applicationContext, serviceScope)
        repository = BewakoofRepository(database)
        securityEngine = SecurityEngine()
        toolRegistry = ToolRegistry()
        aiRouter = AiRouter()
        taskEngine = TaskEngine(
            repository = repository,
            securityEngine = securityEngine,
            toolRegistry = toolRegistry,
            aiRouter = aiRouter,
            scope = serviceScope
        )

        initTts()
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Bewakoof::VoiceWakeLock")
    }

    private fun initTts() {
        textToSpeech = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale("hi", "IN")
                isTtsReady = true
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Bewakoof Background Voice Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps voice agent active in background to wake without opening app"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, BewakoofWakeService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val emergencyIntent = Intent(this, BewakoofWakeService::class.java).apply {
            action = ACTION_EMERGENCY_STOP
        }
        val pendingEmergency = PendingIntent.getService(
            this, 2, emergencyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Bewakoof Voice Guard Active")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingOpenApp)
            .addAction(R.drawable.ic_launcher_foreground, "Emergency Stop", pendingEmergency)
            .addAction(R.drawable.ic_launcher_foreground, "Turn Off", pendingStop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForegroundListening()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_EMERGENCY_STOP -> {
                taskEngine.emergencyStopAll()
                speak("Emergency stop executed in background.")
                return START_STICKY
            }
            else -> {
                startForegroundListening()
            }
        }
        return START_STICKY
    }

    private fun startForegroundListening() {
        _isServiceRunning.value = true
        val notification = buildNotification("Listening in background for 'Bewakoof'...")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        wakeLock?.acquire(24 * 60 * 60 * 1000L) // 24 hours
        isListeningLoopActive = true
        startRecognizerLoop()
    }

    private fun startRecognizerLoop() {
        if (!isListeningLoopActive) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        // Restart recognition after brief backoff
                        if (isListeningLoopActive) {
                            serviceScope.launch {
                                delay(1200)
                                startRecognizerLoop()
                            }
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        if (text.isNotBlank()) {
                            handleRecognizedVoiceCommand(text)
                        } else if (isListeningLoopActive) {
                            startRecognizerLoop()
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e("BewakoofWakeService", "Speech recognizer error", e)
            if (isListeningLoopActive) {
                serviceScope.launch {
                    delay(2000)
                    startRecognizerLoop()
                }
            }
        }
    }

    private fun handleRecognizedVoiceCommand(rawTranscript: String) {
        val lower = rawTranscript.lowercase()
        // If transcript contains wake word "bewakoof" or assistant command
        val cleanedGoal = if (lower.contains("bewakoof")) {
            rawTranscript.replace(Regex("(?i)bewakoof"), "").trim()
        } else {
            rawTranscript.trim()
        }

        val goalToExecute = cleanedGoal.ifBlank { "phone status" }

        taskEngine.processGoal(
            context = applicationContext,
            rawGoal = goalToExecute,
            simulatedSpeakerSignature = "VOICE_SIG_OWNER",
            onOutputMessage = { reply, _ ->
                speak(reply)
                // Resume loop after speech
                serviceScope.launch {
                    delay(3000)
                    if (isListeningLoopActive) {
                        startRecognizerLoop()
                    }
                }
            }
        )
    }

    private fun speak(text: String) {
        if (isTtsReady) {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "service_utterance")
        }
    }

    private fun stopForegroundListening() {
        isListeningLoopActive = false
        _isServiceRunning.value = false
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
            wakeLock?.release()
        } catch (_: Exception) {}
        stopForeground(true)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForegroundListening()
        textToSpeech?.shutdown()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
