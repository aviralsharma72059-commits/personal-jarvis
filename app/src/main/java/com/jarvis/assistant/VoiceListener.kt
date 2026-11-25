package com.jarvis.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class VoiceListener(private val ctx: Context, private val cb: Callback) {

    interface Callback {
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onError(code: Int)
    }

    private var rec: SpeechRecognizer? = null

    fun start(lang: String) {
        stop()

        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            cb.onError(SpeechRecognizer.ERROR_CLIENT)
            return
        }

        val r = SpeechRecognizer.createSpeechRecognizer(ctx)

        rec = r

        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                if (rec !== r) return
                rec = null
                r.destroy()
                cb.onFinal(first(results))
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (rec !== r) return
                val t = first(partialResults)
                if (t.isNotBlank()) cb.onPartial(t)
            }

            override fun onError(error: Int) {
                if (rec !== r) return
                rec = null
                r.destroy()
                cb.onError(error)
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        )
    }

    fun stop() {
        val old = rec
        rec = null
        old?.cancel()
        old?.destroy()
    }

    private fun first(b: Bundle?): String =
        b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull().orEmpty()
}
