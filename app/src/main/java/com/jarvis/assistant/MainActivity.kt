package com.jarvis.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val Cyan = Color(0xFF00E5FF)
private val Bg = Color(0xFF050B14)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(primary = Cyan, background = Bg, surface = Color(0xFF0B1F33))
            ) { JarvisScreen(jarvis) }
        }
    }

    override fun onStart() { super.onStart(); AppState.foreground = true }
    override fun onStop() { AppState.foreground = false; super.onStop() }
}

@Composable
fun JarvisScreen(engine: JarvisEngine) {
    val ctx = LocalContext.current
    val msgs by engine.messages.collectAsStateWithLifecycle()
    val status by engine.status.collectAsStateWithLifecycle()
    val partial by engine.partial.collectAsStateWithLifecycle()
    val lang by engine.lang.collectAsStateWithLifecycle()
    val wake by engine.wake.collectAsStateWithLifecycle()

    var input by remember { mutableStateOf("") }
    var keyField by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(engine.prefs.apiKey.isBlank()) }
    var showRationale by remember { mutableStateOf(false) }
    var showDenied by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val listState = rememberLazyListState()

    fun hasMic() = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasMic()) pending?.invoke() else showDenied = true
        pending = null
    }

    fun withMic(action: () -> Unit) {
        if (hasMic()) action() else { pending = action; showRationale = true }
    }

    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.lastIndex) }

    val label = when {
        status == Status.IDLE && wake -> "STANDING BY  •  SAY \"JARVIS\""
        else -> status.name
    }

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Bg, Color(0xFF0A1A2B))))
            .systemBarsPadding().imePadding().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("J.A.R.V.I.S", color = Cyan, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp)
                Text(label, color = Cyan.copy(alpha = 0.6f), fontSize = 11.sp, letterSpacing = 2.sp)
            }
            TextButton(onClick = { engine.setLang(if (lang == "en-IN") "hi-IN" else "en-IN") }) {
                Text(if (lang == "en-IN") "EN" else "हिं", color = Cyan, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = { keyField = ""; showKey = true }) { Text("⚙", color = Cyan, fontSize = 20.sp) }
        }

        Reactor(status, Modifier.padding(vertical = 8.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Voice activation (\"Jarvis\")", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp,
                modifier = Modifier.weight(1f))
            Switch(checked = wake, onCheckedChange = { on ->
                if (on) withMic { ctx.startForegroundService(Intent(ctx, WakeService::class.java)) }
                else ctx.stopService(Intent(ctx, WakeService::class.java))
            })
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState) {
            items(msgs) { Bubble(it) }
            if (partial.isNotBlank()) item { Bubble(ChatMsg(true, "$partial…")) }
        }

        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type or tap the mic…", color = Color.White.copy(alpha = 0.4f)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = Cyan, unfocusedBorderColor = Cyan.copy(alpha = 0.4f), cursorColor = Cyan
                )
            )
            TextButton(onClick = { engine.submit(input); input = "" }) { Text("▶", color = Cyan, fontSize = 22.sp) }
            Box(
                Modifier.size(52.dp).clip(CircleShape)
                    .background(if (status == Status.LISTENING) Color(0xFF00FF9C) else Cyan)
                    .clickable {
                        if (status == Status.LISTENING) engine.stopListening() else withMic { engine.listenOnce() }
                    },
                contentAlignment = Alignment.Center
            ) { Text("🎙", fontSize = 22.sp) }
        }
    }

    if (showKey) {
        AlertDialog(
            onDismissRequest = { if (engine.prefs.apiKey.isNotBlank()) showKey = false },
            title = { Text("Claude API key") },
            text = {
                Column {
                    Text("Stored encrypted on this device. Get a key at console.anthropic.com.", fontSize = 12.sp)
                    OutlinedTextField(
                        value = keyField, onValueChange = { keyField = it }, singleLine = true,
                        placeholder = { Text(if (engine.prefs.apiKey.isBlank()) "sk-ant-..." else "Saved (enter new to replace)") },
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (keyField.isNotBlank()) engine.prefs.apiKey = keyField
                    showKey = false
                }) { Text("Save") }
            }
        )
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false; pending = null },
            title = { Text("Microphone access") },
            text = { Text("Jarvis needs the microphone to hear your voice commands. Speech is converted to text by Android's speech recognizer, and only that text is sent to Claude. Nothing is recorded or stored.") },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
                    if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
                    permLauncher.launch(perms.toTypedArray())
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { showRationale = false; pending = null }) { Text("Not now") } }
        )
    }

    if (showDenied) {
        AlertDialog(
            onDismissRequest = { showDenied = false },
            title = { Text("Permission needed") },
            text = { Text("Voice features are off because microphone access was denied. You can enable it in App settings. Typing still works.") },
            confirmButton = {
                TextButton(onClick = {
                    showDenied = false
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                    )
                }) { Text("Open settings") }
            },
            dismissButton = { TextButton(onClick = { showDenied = false }) { Text("Close") } }
        )
    }
}

@Composable
fun Bubble(m: ChatMsg) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start
    ) {
        val shape = RoundedCornerShape(14.dp)
        Text(
            m.text, color = Color.White,
            modifier = Modifier.widthIn(max = 300.dp).clip(shape)
                .background(if (m.fromUser) Color(0xFF12304A) else Color(0xFF0B1F33))
                .border(1.dp, Cyan.copy(alpha = if (m.fromUser) 0.25f else 0.6f), shape)
                .padding(12.dp)
        )
    }
}

/** Animated arc-reactor style orb; colour and speed follow the assistant status. */
@Composable
fun Reactor(status: Status, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "reactor")
    val speed = if (status == Status.THINKING) 1800 else 7000
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(speed, easing = LinearEasing)), label = "rot")
    val pulse by t.animateFloat(
        0.88f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse"
    )
    val color = when (status) {
        Status.LISTENING -> Color(0xFF00FF9C)
        Status.THINKING -> Color(0xFFFFB300)
        Status.SPEAKING -> Color(0xFFB388FF)
        Status.IDLE -> Cyan
    }
    Canvas(modifier.size(180.dp)) {
        val c = center
        val r = size.minDimension / 2
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.45f), Color.Transparent), c, r), r * pulse)
        rotate(rot) {
            for (a in listOf(0f, 180f)) {
                drawArc(color, a, 100f, false, Offset(c.x - r * 0.9f, c.y - r * 0.9f), Size(r * 1.8f, r * 1.8f),
                    style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        rotate(-rot * 1.6f) {
            for (a in listOf(60f, 240f)) {
                drawArc(color.copy(alpha = 0.8f), a, 70f, false, Offset(c.x - r * 0.62f, c.y - r * 0.62f), Size(r * 1.24f, r * 1.24f),
                    style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        drawCircle(color, r * 0.3f, c, style = Stroke(2.dp.toPx()))
        drawCircle(color, r * 0.16f * pulse, c)
    }
}
