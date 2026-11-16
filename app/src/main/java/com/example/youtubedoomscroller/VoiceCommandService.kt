package com.example.youtubedoomscroller

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/** Start from MainActivity while visible; the accessibility service never starts the mic. */
class VoiceCommandService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val prefs by lazy { getSharedPreferences(DoomSettings.FILE, MODE_PRIVATE) }
    private var voice: VoiceCommandManager? = null
    private var terminalStatus: String? = null

    override fun onCreate() { super.onCreate(); prefs.registerOnSharedPreferenceChangeListener(this) }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            prefs.edit().putBoolean(DoomSettings.SESSION, false).putBoolean(DoomSettings.VOICE, false).apply()
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_PAUSE) {
            prefs.edit().putBoolean(DoomSettings.SESSION, false).apply()
            return START_NOT_STICKY
        }
        if (!prefs.getBoolean(DoomSettings.VOICE, false) ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            prefs.edit().putBoolean(DoomSettings.VOICE, false).apply()
            stopSelf()
            return START_NOT_STICKY
        }
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Hands-free voice controls", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, VoiceCommandService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val pause = PendingIntent.getService(this, 2, Intent(this, VoiceCommandService::class.java).setAction(ACTION_PAUSE), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("Doomscroller voice controls")
            .setContentText("Microphone active. Say Scroller pause to pause scrolling.")
            .setContentIntent(open).setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Pause scrolling", pause)
            .addAction(android.R.drawable.ic_media_pause, "Stop session & mic", stop).build()
        try {
            if (Build.VERSION.SDK_INT >= 30) startForeground(24, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(24, notification)
        } catch (_: RuntimeException) {
            terminalStatus = "Open the app and enable voice to start the microphone"
            prefs.edit().putBoolean(DoomSettings.VOICE, false).putString(DoomSettings.VOICE_STATUS, terminalStatus).apply()
            stopSelf()
            return START_NOT_STICKY
        }
        if (voice == null) {
            voice = VoiceCommandManager(this,
                requireWakeWord = { prefs.getBoolean(DoomSettings.WAKE_WORD, true) },
                onCommand = { command ->
                    sendBroadcast(Intent(packageName + DoomSettings.COMMAND_SUFFIX).setPackage(packageName)
                        .putExtra(DoomSettings.COMMAND_EXTRA, command.name))
                },
                onStatus = { prefs.edit().putString(DoomSettings.VOICE_STATUS, it).apply() },
                onStopped = { message ->
                    terminalStatus = message
                    prefs.edit().putBoolean(DoomSettings.VOICE, false).putBoolean(DoomSettings.SESSION, false).apply()
                    stopSelf()
                })
            voice?.startListening()
        }
        return START_NOT_STICKY
    }
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == DoomSettings.VOICE && !prefs.getBoolean(DoomSettings.VOICE, false)) stopSelf()
    }
    override fun onDestroy() {
        voice?.stopListening(); voice = null
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        prefs.edit().putBoolean(DoomSettings.VOICE, false).putBoolean(DoomSettings.SESSION, false)
            .putString(DoomSettings.VOICE_STATUS, terminalStatus ?: "Microphone off").apply()
        super.onDestroy()
    }
    companion object {
        private const val CHANNEL = "doomscroller_voice"
        const val ACTION_STOP = "com.example.youtubedoomscroller.STOP_VOICE"
        const val ACTION_PAUSE = "com.example.youtubedoomscroller.PAUSE"
    }
}
