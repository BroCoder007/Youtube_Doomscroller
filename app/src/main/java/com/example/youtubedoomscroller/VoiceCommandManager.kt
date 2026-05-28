package com.example.youtubedoomscroller

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class VoiceCommandManager(
    private val context: Context,
    private val onCommand: (String) -> Unit
) {
    private val TAG = "VoiceCommandManager"
    private var speechRecognizer: SpeechRecognizer? = null
    private val recognizerIntent: Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    fun startListening() {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { Log.d(TAG, "Ready for speech") }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    Log.e(TAG, "Error: $error")
                    // Restart listening on error
                    startListening()
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    matches?.firstOrNull()?.let {
                        Log.d(TAG, "Result: $it")
                        processCommand(it)
                    }
                    // Continue listening
                    startListening()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    matches?.firstOrNull()?.let {
                        Log.d(TAG, "Partial Result: $it")
                        processCommand(it)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        speechRecognizer?.startListening(recognizerIntent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun processCommand(text: String) {
        val lowercaseText = text.lowercase()
        if (lowercaseText.contains("like")) {
            onCommand("like")
        } else if (lowercaseText.contains("comment")) {
            onCommand("comment")
        }
    }
}
