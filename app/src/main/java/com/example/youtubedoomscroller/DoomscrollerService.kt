package com.example.youtubedoomscroller

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class DoomscrollerService : AccessibilityService() {

    private val TAG = "DoomscrollerService"
    private val CHANNEL_ID = "DoomscrollerServiceChannel"
    private val NOTIFICATION_ID = 1
    
    private val handler = Handler(Looper.getMainLooper())
    private var scrollRunnable: Runnable? = null
    private lateinit var sharedPreferences: SharedPreferences
    private var voiceCommandManager: VoiceCommandManager? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service Connected")
        sharedPreferences = getSharedPreferences("doomscroller_prefs", Context.MODE_PRIVATE)
        
        createNotificationChannel()
        
        // Only start foreground service if voice is enabled and permission is granted
        if (sharedPreferences.getBoolean("voice_enabled", false) && hasAudioPermission()) {
            startForegroundService()
            setupVoiceCommands()
        }
        
        startAutoScroll()
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Doomscroller Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun startForegroundService() {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Doomscroller Active")
            .setContentText("Listening for voice commands and auto-scrolling...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to start foreground service: ${e.message}")
        }
    }

    private fun startAutoScroll() {
        scrollRunnable?.let { handler.removeCallbacks(it) }
        
        val delay = sharedPreferences.getFloat("scroll_delay", 10f).toLong() * 1000
        
        scrollRunnable = object : Runnable {
            override fun run() {
                if (isAtShortsOrReels()) {
                    performScroll()
                } else {
                    Log.d(TAG, "Not at Shorts or Reels, skipping scroll")
                }
                val currentDelay = sharedPreferences.getFloat("scroll_delay", 10f).toLong() * 1000
                handler.postDelayed(this, currentDelay)
            }
        }
        handler.postDelayed(scrollRunnable!!, delay)
    }

    private fun isAtShortsOrReels(): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        
        // YouTube Shorts detection
        val ytShortsIds = listOf(
            "com.google.android.youtube:id/reel_watch_fragment_root",
            "com.google.android.youtube:id/reel_recycler",
            "com.google.android.youtube:id/shorts_player_view"
        )
        for (id in ytShortsIds) {
            if (rootNode.findAccessibilityNodeInfosByViewId(id).isNotEmpty()) return true
        }
        if (rootNode.findAccessibilityNodeInfosByText("Remix").isNotEmpty()) return true

        // Instagram Reels detection
        val instaReelsIds = listOf(
            "com.instagram.android:id/reels_video_container",
            "com.instagram.android:id/reels_viewer_pager"
        )
        for (id in instaReelsIds) {
            if (rootNode.findAccessibilityNodeInfosByViewId(id).isNotEmpty()) return true
        }

        return false
    }

    private fun setupVoiceCommands() {
        if (sharedPreferences.getBoolean("voice_enabled", false)) {
            voiceCommandManager = VoiceCommandManager(this) { command ->
                when (command) {
                    "like" -> performLike()
                    "comment" -> openComments()
                }
            }
            voiceCommandManager?.startListening()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Potentially update state if user toggles voice commands in UI
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (sharedPreferences.getBoolean("voice_enabled", false) && voiceCommandManager == null && hasAudioPermission()) {
                startForegroundService()
                setupVoiceCommands()
            } else if (!sharedPreferences.getBoolean("voice_enabled", false) && voiceCommandManager != null) {
                stopForeground(true)
                voiceCommandManager?.stopListening()
                voiceCommandManager = null
            }
        }
    }

    override fun onInterrupt() {
        scrollRunnable?.let { handler.removeCallbacks(it) }
        voiceCommandManager?.stopListening()
    }

    private fun performScroll() {
        Log.d(TAG, "Performing Scroll")
        val displayMetrics: DisplayMetrics = resources.displayMetrics
        val height = displayMetrics.heightPixels
        val width = displayMetrics.widthPixels

        val path = Path()
        path.moveTo(width / 2f, height * 0.8f)
        path.lineTo(width / 2f, height * 0.2f)

        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 100, 300))
        dispatchGesture(gestureBuilder.build(), null, null)
    }

    fun performLike() {
        val rootNode = rootInActiveWindow ?: return
        findNodeByContentDescription(rootNode, listOf("like this video", "unlike this video", "Like"))?.let {
            it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.d(TAG, "Liked video")
        }
    }

    fun openComments() {
        val rootNode = rootInActiveWindow ?: return
        findNodeByText(rootNode, "Comments")?.let {
            it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.d(TAG, "Opened comments")
        }
    }

    private fun findNodeByContentDescription(node: AccessibilityNodeInfo, keywords: List<String>): AccessibilityNodeInfo? {
        val description = node.contentDescription?.toString()?.lowercase()
        if (description != null) {
            for (keyword in keywords) {
                if (description.contains(keyword.lowercase())) return node
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeByContentDescription(child, keywords)
            if (result != null) return result
        }
        return null
    }

    private fun findNodeByText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodeText = node.text?.toString()?.lowercase()
        if (nodeText != null && nodeText.contains(text.lowercase())) return node
        
        val description = node.contentDescription?.toString()?.lowercase()
        if (description != null && description.contains(text.lowercase())) return node

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeByText(child, text)
            if (result != null) return result
        }
        return null
    }
}
