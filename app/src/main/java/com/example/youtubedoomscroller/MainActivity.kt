package com.example.youtubedoomscroller

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.youtubedoomscroller.ui.theme.YoutubeDoomscrollerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YoutubeDoomscrollerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    DoomscrollerSettings(this, Modifier.padding(padding))
                }
            }
        }
    }
}

private fun accessibilityEnabled(context: Context): Boolean {
    val component = ComponentName(context, DoomscrollerService::class.java)
    return Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        ?.split(':')?.any { ComponentName.unflattenFromString(it) == component } == true
}

@Composable
fun DoomscrollerSettings(activity: ComponentActivity, modifier: Modifier = Modifier) {
    val prefs = remember { activity.getSharedPreferences("doomscroller_prefs", Context.MODE_PRIVATE) }
    var revision by remember { mutableIntStateOf(0) }
    var accessEnabled by remember { mutableStateOf(accessibilityEnabled(activity)) }
    var notice by remember { mutableStateOf<String?>(null) }
    DisposableEffect(prefs, activity) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessEnabled = accessibilityEnabled(activity)
                revision++
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        activity.lifecycle.addObserver(observer)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            activity.lifecycle.removeObserver(observer)
        }
    }
    @Suppress("UNUSED_VARIABLE") val refresh = revision
    val running = prefs.getBoolean("session_enabled", false)
    val smart = prefs.getBoolean("smart_mode", true)
    val voice = prefs.getBoolean("voice_enabled", false)
    val wakeWord = prefs.getBoolean("require_wake_word", true)
    val delay = prefs.getFloat("scroll_delay", 10f).coerceIn(5f, 120f)
    val limit = prefs.getInt("session_limit_minutes", 0)

    fun startVoice() {
        try {
            ContextCompat.startForegroundService(activity, Intent(activity, VoiceCommandService::class.java))
            notice = null
        } catch (error: RuntimeException) {
            prefs.edit().putBoolean("voice_enabled", false).apply()
            notice = "Microphone could not start. Keep this screen open and try again."
        }
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val granted = permissions[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        prefs.edit().putBoolean("voice_enabled", granted).apply()
        if (granted) startVoice() else notice = "Microphone access was declined. You can still use auto-scroll."
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("HANDS FREE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text("Doomscroller", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Watch the whole clip. Move on when it ends.", style = MaterialTheme.typography.bodyLarge)
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (running) "Session running" else "Session paused", style = MaterialTheme.typography.titleLarge)
                Text(prefs.getString("runtime_status", "Enable accessibility, start a session, then open a short or reel.") ?: "")
                Button(onClick = {
                    if (running) prefs.edit().putBoolean("session_enabled", false).apply()
                    else {
                        prefs.edit().putBoolean("session_enabled", true).apply()
                        if (voice && ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startVoice()
                    }
                }, enabled = accessEnabled || running, modifier = Modifier.fillMaxWidth()) {
                    Text(if (running) "Pause auto-scroll" else "Start auto-scroll")
                }
                if (!accessEnabled) Text("Enable Doomscroller in Accessibility settings to start.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Playback", style = MaterialTheme.typography.titleLarge)
            SettingToggle("Smart mode", "Wait for observed video completion. If progress is unavailable, wait for your command.", smart) {
                prefs.edit().putBoolean("smart_mode", it).apply()
            }
            if (!smart) {
                Text("Timer: ${delay.toInt()} seconds")
                Slider(value = delay, onValueChange = { prefs.edit().putFloat("scroll_delay", it).apply() }, valueRange = 5f..120f)
                Text("Timer mode may skip before a clip ends.", style = MaterialTheme.typography.bodySmall)
            }
            Text("Session limit", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 15, 30, 60).forEach { minutes ->
                    FilterChip(selected = limit == minutes, onClick = { prefs.edit().putInt("session_limit_minutes", minutes).apply() }, label = { Text(if (minutes == 0) "Off" else "${minutes}m") })
                }
            }
        }
        HorizontalDivider()
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Voice controls", style = MaterialTheme.typography.titleLarge)
            SettingToggle("Listen for commands", "Starts the microphone while this screen is open. Listening continues with a visible notification.", voice) { enabled ->
                if (enabled) {
                    val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
                    if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                    microphonePermission.launch(permissions.toTypedArray())
                } else {
                    prefs.edit().putBoolean("voice_enabled", false).apply()
                    activity.stopService(Intent(activity, VoiceCommandService::class.java))
                }
            }
            if (voice) Text(prefs.getString("voice_status", "Starting microphone…") ?: "", style = MaterialTheme.typography.bodySmall)
            SettingToggle("Require “scroller”", "Helps prevent video dialogue from triggering commands.", wakeWord) {
                prefs.edit().putBoolean("require_wake_word", it).apply()
            }
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Say “scroller pause”", fontWeight = FontWeight.SemiBold)
                    Text("Also: resume · next · previous · like · comment · smart mode · timer mode · stop")
                    Text("Commands pause auto-scroll until you say resume. Next and previous move once. Comment opens comments without posting. Use headphones for better recognition.", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("Speech recognition is supplied by Android. It prefers on-device recognition when available; other providers may process speech online.", style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider()
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Setup", style = MaterialTheme.typography.titleLarge)
            Text(if (accessEnabled) "✓ Accessibility is enabled" else "Accessibility is required")
            Text("Doomscroller reads the visible app’s controls and playback progress and can swipe or tap in supported short-video views. Enable it only if you agree to this access.", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) { Text("Open Accessibility settings") }
            Text("YouTube Shorts · Instagram Reels · TikTok", style = MaterialTheme.typography.labelLarge)
            Text("Native apps expose different progress information. Smart mode cannot guarantee completion detection on every app version. It never substitutes a fixed timer.", style = MaterialTheme.typography.bodySmall)
        }
        notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SettingToggle(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
