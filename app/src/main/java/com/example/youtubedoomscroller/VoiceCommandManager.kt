package com.example.youtubedoomscroller

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Recognition is opt-in and owned by a stoppable microphone foreground service. */
class VoiceCommandManager(
    private val context: Context,
    private val requireWakeWord: () -> Boolean,
    private val onCommand: (VoiceCommand) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onStopped: (String) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var active = false
    private var errors = 0
    private val restart = Runnable { if (active) listen() }
    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
    }

    fun startListening() {
        if (active) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            fail("Speech recognition is unavailable on this device")
            return
        }
        active = true
        errors = 0
        try {
            recognizer = if (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else SpeechRecognizer.createSpeechRecognizer(context)
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    onStatus(if (requireWakeWord()) "Listening — say Scroller, then a command" else "Listening for commands")
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onPartialResults(partialResults: Bundle?) {} // Never act on partial transcripts.
                override fun onResults(results: Bundle?) {
                    if (!active) return
                    errors = 0
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        ?.let { CommandParser.parse(it, requireWakeWord()) }?.let(onCommand)
                    schedule(400L)
                }
                override fun onError(error: Int) {
                    if (!active) return
                    if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ||
                        error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                        error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE) {
                        fail("Voice unavailable (error $error). Re-enable voice in the app.")
                        return
                    }
                    if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        schedule(600L)
                    } else {
                        errors++
                        if (errors >= 5) {
                            fail("Voice stopped after repeated errors. Re-enable voice in the app.")
                        } else {
                            onStatus("Voice retrying ($errors/5)")
                            schedule((1_000L shl errors.coerceAtMost(4)).coerceAtMost(15_000L))
                        }
                    }
                }
            })
            listen()
        } catch (_: RuntimeException) {
            fail("Voice could not start. Check microphone permission and speech service.")
        }
    }

    private fun schedule(delay: Long) { handler.removeCallbacks(restart); if (active) handler.postDelayed(restart, delay) }
    private fun listen() {
        if (!active) return
        try { recognizer?.startListening(intent) }
        catch (_: RuntimeException) { fail("Voice could not start. Re-enable voice in the app.") }
    }
    private fun fail(message: String) { stopListening(); onStatus(message); onStopped(message) }
    fun stopListening() {
        active = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }
}
