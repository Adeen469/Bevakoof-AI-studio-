package com.example.voice

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class AgentVoiceState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    EMERGENCY_STOPPED,
    ERROR
}

data class VoiceAcousticStats(
    val rmsLevel: Float = 0f,
    val peakLevel: Float = 0f,
    val spectralBandEstimate: Float = 0f
)

class VoiceDspEngine(
    private val context: Context,
    private val onTranscriptRecognized: (transcript: String, isFinal: Boolean) -> Unit,
    private val onError: (errorMessage: String) -> Unit
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    private val _voiceState = MutableStateFlow(AgentVoiceState.IDLE)
    val voiceState: StateFlow<AgentVoiceState> = _voiceState.asStateFlow()

    private val _acousticStats = MutableStateFlow(VoiceAcousticStats())
    val acousticStats: StateFlow<VoiceAcousticStats> = _acousticStats.asStateFlow()

    private val _activeAudioRoute = MutableStateFlow("Speaker")
    val activeAudioRoute: StateFlow<String> = _activeAudioRoute.asStateFlow()

    init {
        initTts()
        updateAudioRouteState()
    }

    private fun initTts() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Try Hindi first, fall back to English
                val hindiLocale = Locale("hi", "IN")
                val result = textToSpeech?.setLanguage(hindiLocale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    textToSpeech?.setLanguage(Locale.US)
                }
                textToSpeech?.setPitch(1.0f)
                textToSpeech?.setSpeechRate(1.0f)
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _voiceState.value = AgentVoiceState.SPEAKING
                    }

                    override fun onDone(utteranceId: String?) {
                        if (_voiceState.value == AgentVoiceState.SPEAKING) {
                            _voiceState.value = AgentVoiceState.IDLE
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _voiceState.value = AgentVoiceState.IDLE
                    }
                })
                isTtsReady = true
            }
        }
    }

    fun startListening() {
        stopTts()
        if (_voiceState.value == AgentVoiceState.EMERGENCY_STOPPED) {
            _voiceState.value = AgentVoiceState.IDLE
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition not available on this device")
            return
        }

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _voiceState.value = AgentVoiceState.LISTENING
                    }

                    override fun onBeginningOfSpeech() {
                        _voiceState.value = AgentVoiceState.LISTENING
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                        _acousticStats.value = VoiceAcousticStats(
                            rmsLevel = normalized,
                            peakLevel = (normalized * 1.3f).coerceIn(0f, 1f),
                            spectralBandEstimate = normalized * 2400f + 300f
                        )
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _voiceState.value = AgentVoiceState.PROCESSING
                    }

                    override fun onError(error: Int) {
                        _voiceState.value = AgentVoiceState.IDLE
                        _acousticStats.value = VoiceAcousticStats()
                        val msg = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                            SpeechRecognizer.ERROR_NETWORK -> "Network issue in recognition"
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic permission required"
                            else -> "Voice recognition paused"
                        }
                        // Don't toast aggressively on silence
                        if (error != SpeechRecognizer.ERROR_NO_MATCH) {
                            onError(msg)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        _voiceState.value = AgentVoiceState.PROCESSING
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        if (text.isNotBlank()) {
                            onTranscriptRecognized(text, true)
                        } else {
                            _voiceState.value = AgentVoiceState.IDLE
                        }
                        _acousticStats.value = VoiceAcousticStats()
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        matches?.firstOrNull()?.let { text ->
                            if (text.isNotBlank()) {
                                onTranscriptRecognized(text, false)
                            }
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            _voiceState.value = AgentVoiceState.IDLE
            onError("Mic error: ${e.localizedMessage}")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {}
        _acousticStats.value = VoiceAcousticStats()
        if (_voiceState.value == AgentVoiceState.LISTENING) {
            _voiceState.value = AgentVoiceState.IDLE
        }
    }

    fun speak(text: String, utteranceId: String = "bewakoof_reply") {
        if (!isTtsReady) return
        stopListening()
        _voiceState.value = AgentVoiceState.SPEAKING
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stopTts() {
        try {
            textToSpeech?.stop()
        } catch (_: Exception) {}
        if (_voiceState.value == AgentVoiceState.SPEAKING) {
            _voiceState.value = AgentVoiceState.IDLE
        }
    }

    /**
     * Emergency Stop: immediately halts recognition, speech synthesis, and audio pipelines
     */
    fun emergencyHalt() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
            textToSpeech?.stop()
        } catch (_: Exception) {}
        _voiceState.value = AgentVoiceState.EMERGENCY_STOPPED
        _acousticStats.value = VoiceAcousticStats()
    }

    /**
     * Toggles between normal loudspeaker and private call-like earpiece mode
     */
    fun setAudioRoute(isEarpieceMode: Boolean) {
        try {
            if (isEarpieceMode) {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager.isSpeakerphoneOn = false
                _activeAudioRoute.value = "Earpiece (Call Mode)"
            } else {
                audioManager.mode = AudioManager.MODE_NORMAL
                audioManager.isSpeakerphoneOn = true
                _activeAudioRoute.value = "Speaker"
            }
        } catch (e: Exception) {
            updateAudioRouteState()
        }
    }

    private fun updateAudioRouteState() {
        val isBt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            devices.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
        } else {
            audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn
        }

        _activeAudioRoute.value = when {
            isBt -> "Bluetooth Headset"
            audioManager.mode == AudioManager.MODE_IN_COMMUNICATION -> "Earpiece"
            else -> "Speaker"
        }
    }

    /**
     * Computes a simulated acoustic feature fingerprint for speaker enrollment/verification
     */
    fun computeSpeakerSignature(sampleTranscript: String): String {
        val hash = sampleTranscript.trim().lowercase().hashCode()
        return "VOICE_SIG_${Math.abs(hash % 999999)}"
    }

    fun release() {
        try {
            speechRecognizer?.destroy()
            textToSpeech?.shutdown()
        } catch (_: Exception) {}
    }
}
