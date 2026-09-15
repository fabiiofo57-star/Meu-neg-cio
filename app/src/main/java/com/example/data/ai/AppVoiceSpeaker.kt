package com.example.data.ai

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Motor de Fala (Text-To-Speech) em Português para 'Sua assistente pessoal de negócios'.
 * Permite que a assistente responda por voz aos comandos, dúvidas e ideias do empreendedor.
 */
class AppVoiceSpeaker(context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking = _isSpeaking.asStateFlow()

    init {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(Locale("pt", "BR"))
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.setLanguage(Locale.getDefault())
                    }
                    tts?.setSpeechRate(1.05f)
                    tts?.setPitch(1.0f)

                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _isSpeaking.value = true
                        }

                        override fun onDone(utteranceId: String?) {
                            _isSpeaking.value = false
                        }

                        override fun onError(utteranceId: String?) {
                            _isSpeaking.value = false
                        }
                    })
                    isInitialized = true
                } else {
                    Log.w("AppVoiceSpeaker", "TTS init failed with status: $status")
                }
            }
        } catch (e: Exception) {
            Log.e("AppVoiceSpeaker", "Error initializing TextToSpeech", e)
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        stop()
        if (isInitialized && tts != null) {
            // Limpar caracteres estranhos e markdown para a fala soar fluida e natural
            val cleaned = text
                .replace(Regex("(?i)[*#_`~>|]"), " ")
                .replace("R$", "reais")
                .replace(Regex("\\s+"), " ")
                .trim()

            _isSpeaking.value = true
            tts?.speak(cleaned, TextToSpeech.QUEUE_FLUSH, null, "ASSISTANT_UTTERANCE_${System.currentTimeMillis()}")
        }
    }

    fun stop() {
        try {
            if (tts?.isSpeaking == true) {
                tts?.stop()
            }
        } catch (_: Exception) {}
        _isSpeaking.value = false
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
    }
}
