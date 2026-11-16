package com.jarvis.assistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

class JarvisApp : Application() {
    lateinit var engine: JarvisEngine

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_SERVICE, "Jarvis listening", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "Jarvis alerts", NotificationManager.IMPORTANCE_HIGH)
        )
        engine = JarvisEngine(this)
    }

    companion object {
        const val CH_SERVICE = "jarvis_service"
        const val CH_ALERT = "jarvis_alert"
    }
}

val Context.jarvis: JarvisEngine get() = (applicationContext as JarvisApp).engine
