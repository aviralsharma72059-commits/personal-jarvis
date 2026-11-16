package com.jarvis.assistant

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** Finds an installed launcher app by (fuzzy) name and opens it. */
class AppLauncher(private val ctx: Context) {

    private val aliases = mapOf(
        "व्हाट्सएप" to "whatsapp", "व्हाट्सऐप" to "whatsapp", "वाट्सएप" to "whatsapp",
        "यूट्यूब" to "youtube", "कैमरा" to "camera", "क्रोम" to "chrome", "गूगल" to "google",
        "जीमेल" to "gmail", "मैप्स" to "maps", "सेटिंग्स" to "settings", "सेटिंग" to "settings",
        "कैलकुलेटर" to "calculator", "इंस्टाग्राम" to "instagram", "फेसबुक" to "facebook",
        "टेलीग्राम" to "telegram", "स्पॉटिफाई" to "spotify", "गैलरी" to "gallery",
        "घड़ी" to "clock", "फोन" to "phone", "संदेश" to "messages", "फोटो" to "photos"
    )

    fun open(name: String): String {
        val pm = ctx.packageManager
        val q = (aliases[name.trim()] ?: name).trim().lowercase()
        if (q.isEmpty()) return "No app name was given."

        val apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).map { it.loadLabel(pm).toString() to it.activityInfo.packageName }

        val hit = apps.firstOrNull { it.first.lowercase() == q }
            ?: apps.firstOrNull { it.first.lowercase().contains(q) }
            ?: apps.firstOrNull { it.first.length >= 3 && q.contains(it.first.lowercase()) }
            ?: return "No installed app matches \"$name\"."

        val intent = pm.getLaunchIntentForPackage(hit.second)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return "${hit.first} cannot be launched."

        return if (AppState.foreground) {
            ctx.startActivity(intent)
            "Opened ${hit.first}."
        } else {
            // Android blocks background activity launches, so offer a tap-to-open notification.
            notify(hit.first, intent)
            "Android blocks opening apps from the background; a notification to open ${hit.first} was posted."
        }
    }

    private fun notify(label: String, intent: Intent) {
        val pi = PendingIntent.getActivity(
            ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, JarvisApp.CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat_jarvis)
            .setContentTitle("Jarvis")
            .setContentText("Tap to open $label")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(2, n)
        } catch (e: SecurityException) {
            // Notification permission not granted; nothing else we can do.
        }
    }
}
