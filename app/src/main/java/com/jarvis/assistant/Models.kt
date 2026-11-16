package com.jarvis.assistant

data class ChatMsg(val fromUser: Boolean, val text: String)

enum class Status { IDLE, LISTENING, THINKING, SPEAKING }

/** True while MainActivity is visible. Android only allows launching other apps from the foreground. */
object AppState {
    @Volatile var foreground = false
}
