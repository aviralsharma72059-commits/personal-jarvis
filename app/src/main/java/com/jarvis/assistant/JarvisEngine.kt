package com.jarvis.assistant

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/** Single brain shared by the UI and the wake-word service. Main thread only. */
class JarvisEngine(private val ctx: Context) {

    val prefs = Prefs(ctx)
    private val claude = ClaudeClient(prefs, AppLauncher(ctx))

    val messages = MutableStateFlow<List<ChatMsg>>(emptyList())
    val status = MutableStateFlow(Status.IDLE)
    val partial = MutableStateFlow("")
    val lang = MutableStateFlow(prefs.lang)
    val wake = MutableStateFlow(false)

    private enum class Mode { OFF, WAKE, COMMAND }
    private var mode = Mode.OFF

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val wakeWords = listOf("jarvis", "jarwis", "जार्विस", "जारविस", "जर्विस")

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val voice = VoiceListener(ctx, object : VoiceListener.Callback {
        override fun onPartial(text: String) { partial.value = text }
        override fun onFinal(text: String) { partial.value = ""; handleHeard(text) }
        override fun onError(code: Int) { partial.value = ""; handleError(code) }
    })

    init {
        tts = TextToSpeech(ctx) { result ->
            ttsReady = result == TextToSpeech.SUCCESS
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { main.post { afterSpeak() } }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { main.post { afterSpeak() } }
            })
        }
    }

    // ---------- public controls ----------

    fun setLang(l: String) { lang.value = l; prefs.lang = l }

    fun listenOnce() {
        if (status.value == Status.THINKING) return
        tts?.stop()
        mode = Mode.COMMAND
        status.value = Status.LISTENING
        voice.start(lang.value)
    }

    fun stopListening() { voice.stop(); afterSpeak() }

    fun startWake() { wake.value = true; if (status.value == Status.IDLE) listenWake() }

    fun stopWake() {
        wake.value = false
        if (mode == Mode.WAKE) { voice.stop(); mode = Mode.OFF }
    }

    fun submit(text: String) {
        if (text.isBlank() || status.value == Status.THINKING) return
        voice.stop(); tts?.stop(); mode = Mode.OFF
        add(ChatMsg(true, text.trim()))
        status.value = Status.THINKING
        if (prefs.apiKey.isBlank()) { reply("Please add your Claude API key in settings."); return }
        scope.launch {
            val r = try {
                claude.ask(text)
            } catch (e: Exception) {
                claude.reset()
                "Sorry, I hit a problem. ${e.message ?: ""}"
            }
            reply(r.ifBlank { "Done." })
        }
    }

    // ---------- internals ----------

    private fun listenWake() { mode = Mode.WAKE; status.value = Status.IDLE; voice.start("en-IN") }

    private fun handleHeard(text: String) {
        if (mode == Mode.WAKE) {
            val lower = text.lowercase()
            val w = wakeWords.firstOrNull { lower.contains(it) }
            if (w == null) { listenWake(); return }
            val cmd = text.substring(lower.indexOf(w) + w.length).trim(' ', ',', '.')
            if (cmd.length > 2) submit(cmd) else { beep(); main.postDelayed({ listenOnce() }, 350) }
        } else if (text.isBlank()) afterSpeak() else submit(text)
    }

    private fun handleError(code: Int) {
        when {
            code == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                mode = Mode.OFF; wake.value = false; status.value = Status.IDLE
                add(ChatMsg(false, "Microphone permission is missing. Please allow it in Settings."))
            }
            mode == Mode.WAKE -> {
                val delay = if (code == SpeechRecognizer.ERROR_NO_MATCH ||
                    code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) 300L else 1500L
                main.postDelayed({ if (mode == Mode.WAKE) listenWake() }, delay)
            }
            else -> afterSpeak()
        }
    }

    private fun reply(t: String) { add(ChatMsg(false, t)); speak(t) }

    private fun speak(t: String) {
        val engine = tts
        if (engine == null || !ttsReady) { afterSpeak(); return }
        val hindi = t.any { it in '\u0900'..'\u097F' }
        engine.language = if (hindi) Locale("hi", "IN") else Locale("en", "IN")
        status.value = Status.SPEAKING
        engine.speak(t, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
    }

    private fun afterSpeak() {
        status.value = Status.IDLE
        if (wake.value) listenWake() else mode = Mode.OFF
    }

    private fun beep() {
        try { ToneGenerator(AudioManager.STREAM_MUSIC, 70).startTone(ToneGenerator.TONE_PROP_BEEP, 120) }
        catch (e: RuntimeException) { /* ignore */ }
    }

    private fun add(m: ChatMsg) { messages.value = messages.value + m }
}
