package com.jarvis.assistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Minimal Claude Messages API client with an `open_app` tool. */
class ClaudeClient(private val prefs: Prefs, private val launcher: AppLauncher) {

    companion object {
        const val MODEL = "claude-sonnet-5-5" // change here to use another Claude model
        private const val SYSTEM =
            "You are JARVIS, a concise, witty personal voice assistant on the user's Android phone. " +
            "The user speaks English, Hindi or Hinglish. Reply in the language the user used " +
            "(Hindi in Devanagari script). Replies are spoken aloud, so keep them to one to three " +
            "short sentences, with no markdown, lists or emojis. When the user asks to open or " +
            "launch an app, call the open_app tool, then confirm briefly."
    }

    private val http = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build()
    private val history = JSONArray()

    private val tools = JSONArray().put(
        JSONObject()
            .put("name", "open_app")
            .put("description", "Open an installed Android app by name.")
            .put(
                "input_schema", JSONObject()
                    .put("type", "object")
                    .put(
                        "properties", JSONObject().put(
                            "app_name", JSONObject()
                                .put("type", "string")
                                .put("description", "App name, preferably in English, e.g. WhatsApp")
                        )
                    )
                    .put("required", JSONArray().put("app_name"))
            )
    )

    fun reset() {
        while (history.length() > 0) history.remove(0)
    }

    suspend fun ask(text: String): String = withContext(Dispatchers.IO) {
        history.put(JSONObject().put("role", "user").put("content", text))
        repeat(5) {
            val content = call().getJSONArray("content")
            history.put(JSONObject().put("role", "assistant").put("content", content))

            val out = StringBuilder()
            val results = JSONArray()
            for (i in 0 until content.length()) {
                val b = content.getJSONObject(i)
                when (b.optString("type")) {
                    "text" -> out.append(b.optString("text"))
                    "tool_use" -> {
                        val result = if (b.optString("name") == "open_app") {
                            val app = b.getJSONObject("input").optString("app_name")
                            withContext(Dispatchers.Main) { launcher.open(app) }
                        } else "Unknown tool."
                        results.put(
                            JSONObject().put("type", "tool_result")
                                .put("tool_use_id", b.getString("id"))
                                .put("content", result)
                        )
                    }
                }
            }
            if (results.length() == 0) {
                trim()
                return@withContext out.toString().trim()
            }
            history.put(JSONObject().put("role", "user").put("content", results))
        }
        reset()
        "Sorry, that took too many steps."
    }

    private fun call(): JSONObject {
        val body = JSONObject()
            .put("model", MODEL)
            .put("max_tokens", 512)
            .put("system", SYSTEM)
            .put("tools", tools)
            .put("messages", history)
        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", prefs.apiKey)
            .header("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return http.newCall(req).execute().use { r ->
            val s = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                val msg = runCatching { JSONObject(s).getJSONObject("error").getString("message") }
                    .getOrDefault(s.take(150))
                throw IOException("API ${r.code}: $msg")
            }
            JSONObject(s)
        }
    }

    /** Keep the last ~16 messages, always starting at a plain user message (keeps tool pairs valid). */
    private fun trim() {
        while (history.length() > 16) history.remove(0)
        while (history.length() > 0 && !isPlainUser(history.getJSONObject(0))) history.remove(0)
    }

    private fun isPlainUser(o: JSONObject) = o.optString("role") == "user" && o.get("content") is String
}
