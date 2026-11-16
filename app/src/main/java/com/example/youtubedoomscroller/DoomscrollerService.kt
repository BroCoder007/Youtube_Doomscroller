package com.example.youtubedoomscroller

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat

/** Conservative automation: only supported short-form viewers, never arbitrary app screens. */
class DoomscrollerService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences(DoomSettings.FILE, MODE_PRIVATE) }
    private val completion = CompletionTracker()
    private var connected = false
    private var gestureInFlight = false
    private var nextAllowedAt = 0L
    private var timerStartedAt = 0L
    private var sessionStartedAt = 0L
    private var currentKey: String? = null
    private var sessionHasViewer = false
    private val ticker = object : Runnable {
        override fun run() {
            if (!connected) return
            tick()
            handler.postDelayed(this, 400L)
        }
    }
    private val commands = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val raw = intent?.getStringExtra(DoomSettings.COMMAND_EXTRA) ?: return
            val command = VoiceCommand.entries.firstOrNull { it.name == raw } ?: return
            handleCommand(command)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (connected) return
        connected = true
        prefs.registerOnSharedPreferenceChangeListener(this)
        ContextCompat.registerReceiver(this, commands, IntentFilter(packageName + DoomSettings.COMMAND_SUFFIX), ContextCompat.RECEIVER_NOT_EXPORTED)
        // Reconnecting the OS accessibility service must not restart an old session.
        prefs.edit().putBoolean(DoomSettings.SESSION, false).apply()
        status("Ready — start a session in the app")
        handler.post(ticker)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == DoomSettings.SESSION || key == DoomSettings.SMART || key == DoomSettings.DELAY) {
            resetProgress()
            if (key == DoomSettings.SESSION) {
                sessionHasViewer = false
                sessionStartedAt = if (prefs.getBoolean(DoomSettings.SESSION, false)) SystemClock.elapsedRealtime() else 0L
                if (sessionStartedAt == 0L) status("Paused — say Scroller resume or use Start")
            }
        }
    }

    private fun resetProgress() {
        completion.reset(); currentKey = null; timerStartedAt = SystemClock.elapsedRealtime()
    }

    private fun tick() {
        if (!prefs.getBoolean(DoomSettings.SESSION, false)) return
        if (!deviceReady()) { pause("Device locked or screen off — session paused"); return }
        val now = SystemClock.elapsedRealtime()
        val limit = prefs.getInt(DoomSettings.LIMIT, 0).coerceIn(0, 180)
        if (limit > 0 && sessionStartedAt > 0 && now - sessionStartedAt >= limit * 60_000L) {
            pause("Session limit reached — scrolling paused")
            return
        }
        if (gestureInFlight || now < nextAllowedAt) return
        inspect { screen ->
            if (screen == null || !screen.isShortViewer) {
                resetProgress()
                if (sessionHasViewer) pause("Left the short-form viewer — session paused")
                else status("Waiting for YouTube Shorts, Instagram Reels or TikTok")
                return@inspect
            }
            sessionHasViewer = true
            if (screen.hasEditingSurface) {
                pause("Comments or text entry detected — scrolling paused")
                return@inspect
            }
            if (screen.isPlaybackPaused) {
                resetProgress(); status("Video paused — waiting for playback")
                return@inspect
            }
            if (currentKey != screen.key) {
                completion.reset(); currentKey = screen.key; timerStartedAt = now
            }
            if (prefs.getBoolean(DoomSettings.SMART, true)) {
                val fraction = screen.playbackFraction
                if (fraction == null) {
                    completion.reset()
                    status("Smart mode waiting — this viewer is not exposing playback progress")
                } else {
                    status("Smart mode — watching ${(fraction * 100).toInt()}%")
                    if (completion.observe(PlaybackSample(screen.key, fraction, now))) swipe(screen, forward = true)
                }
            } else {
                val delay = (prefs.getFloat(DoomSettings.DELAY, 10f).takeIf { it.isFinite() } ?: 10f).coerceIn(5f, 120f) * 1_000L
                status("Timer mode — next in ${((delay - (now - timerStartedAt)).coerceAtLeast(0f) / 1_000).toInt() + 1}s")
                if (now - timerStartedAt >= delay) swipe(screen, forward = true)
            }
        }
    }

    private fun handleCommand(command: VoiceCommand) {
        if (!deviceReady()) { pause("Command ignored while device is locked or screen is off"); return }
        when (command) {
            VoiceCommand.RESUME -> {
                resetProgress()
                prefs.edit().putBoolean(DoomSettings.SESSION, true).apply()
                sessionStartedAt = SystemClock.elapsedRealtime()
                status("Resumed — waiting for a short-form video")
            }
            VoiceCommand.PAUSE, VoiceCommand.STOP -> pause("Paused by voice — say Scroller resume to continue")
            else -> {
                pause("Paused by voice command — say Scroller resume to continue")
                when (command) {
                    VoiceCommand.SMART_MODE -> prefs.edit().putBoolean(DoomSettings.SMART, true).apply()
                    VoiceCommand.TIMER_MODE -> prefs.edit().putBoolean(DoomSettings.SMART, false).apply()
                    else -> inspect { screen ->
                        if (screen == null || !screen.isShortViewer) {
                            status("Command ignored — open a supported short-form viewer")
                            return@inspect
                        }
                        when (command) {
                            VoiceCommand.NEXT, VoiceCommand.PREVIOUS -> {
                                if (!screen.hasEditingSurface) swipe(screen, command == VoiceCommand.NEXT)
                                else status("Close comments or text entry before navigating")
                            }
                            VoiceCommand.LIKE -> {
                                val target = screen.nodes.firstOrNull { node ->
                                    val label = screen.label(node)
                                    node.isVisibleToUser && !node.isSelected && !node.isChecked &&
                                        Regex("^(like|like this video|like video|like button)(?:[ ,.]|$)").containsMatchIn(label) &&
                                        !Regex("\\b(unlike|liked|dislike)\\b").containsMatchIn(label)
                                }
                                status(if (click(target)) "Like requested — scrolling paused" else "Like button unavailable or already selected")
                            }
                            VoiceCommand.COMMENT -> {
                                val target = screen.nodes.firstOrNull { node ->
                                    node.isVisibleToUser && Regex("^(comments?|open comments|view comments|view all \\d+ comments)(?:[ ,.]|$)").containsMatchIn(screen.label(node))
                                }
                                status(if (click(target)) "Comments opened — scrolling paused" else "Comments button unavailable")
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    private fun pause(message: String) { prefs.edit().putBoolean(DoomSettings.SESSION, false).apply(); resetProgress(); status(message) }
    private fun status(message: String) {
        if (prefs.getString(DoomSettings.STATUS, null) != message) prefs.edit().putString(DoomSettings.STATUS, message).apply()
    }
    private fun click(node: AccessibilityNodeInfo?): Boolean {
        var target = node ?: return false
        repeat(4) {
            if (target.isClickable && target.isEnabled) return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            target = target.parent ?: return false
        }
        return false
    }

    private fun swipe(screen: Screen, forward: Boolean) {
        if (!deviceReady()) { pause("Device locked or screen off — session paused"); return }
        val now = SystemClock.elapsedRealtime()
        if (gestureInFlight || now < nextAllowedAt) return
        val bounds = Rect().also { screen.root.getBoundsInScreen(it) }
        if (bounds.width() <= 0 || bounds.height() <= 0) return
        val x = bounds.left + bounds.width() * 0.48f
        val top = bounds.top + bounds.height() * 0.27f
        val bottom = bounds.top + bounds.height() * 0.75f
        val path = Path().apply { moveTo(x, if (forward) bottom else top); lineTo(x, if (forward) top else bottom) }
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 280)).build()
        gestureInFlight = true
        val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { gestureInFlight = false; resetProgress() }
            override fun onCancelled(gestureDescription: GestureDescription?) { gestureInFlight = false; resetProgress(); status("Navigation interrupted — waiting for video") }
        }, handler)
        if (!accepted) { gestureInFlight = false; status("Navigation unavailable — retrying after cooldown") }
        nextAllowedAt = now + 1_800L
        resetProgress()
    }

    private fun inspect(block: (Screen?) -> Unit) {
        val root = rootInActiveWindow
        if (root == null) { block(null); return }
        val pkg = root.packageName?.toString().orEmpty()
        if (pkg !in SUPPORTED) { release(root); block(null); return }
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(root to 0)
        while (queue.isNotEmpty() && nodes.size < 350) {
            val (node, depth) = queue.removeFirst()
            nodes.add(node)
            if (depth < 18 && node.isVisibleToUser) {
                for (i in 0 until node.childCount.coerceAtMost(70)) {
                    node.getChild(i)?.let { queue.add(it to depth + 1) }
                }
            }
        }
        try { block(Screen(root, nodes.filter { it.isVisibleToUser }, pkg)) }
        finally { nodes.forEach(::release); queue.forEach { release(it.first) } }
    }

    private class Screen(val root: AccessibilityNodeInfo, val nodes: List<AccessibilityNodeInfo>, val pkg: String) {
        fun label(node: AccessibilityNodeInfo): String = (node.contentDescription ?: node.text)?.toString()?.lowercase().orEmpty().trim()
        private val ids = nodes.mapNotNull { it.viewIdResourceName?.lowercase() }
        val isShortViewer: Boolean = when (pkg) {
            "com.google.android.youtube" -> ids.any { it.contains("reel_watch") || it.contains("reel_recycler") || it.contains("shorts_player") }
            "com.instagram.android" -> ids.any { it.contains("reels_viewer") || it.contains("reels_video") || it.contains("clips_viewer") || it.contains("clips_video") }
            else -> nodes.any { label(it) in setOf("for you", "following", "friends") } &&
                nodes.any { label(it).startsWith("like") || label(it).startsWith("comments") }
        }
        val hasEditingSurface: Boolean = nodes.any {
            it.isEditable || it.isFocused && it.className?.contains("EditText") == true ||
                it.viewIdResourceName?.let { id -> id.contains("comment", true) && (id.contains("sheet", true) || id.contains("input", true) || id.contains("list", true)) } == true
        }
        val isPlaybackPaused: Boolean = nodes.any { label(it) in setOf("play", "play video", "resume video") && it.isEnabled }
        val key: String = pkg + ":" + nodes.filter {
            it.viewIdResourceName?.let { id -> id.contains("author", true) || id.contains("reel_title", true) || id.contains("video_title", true) || id.contains("channel_name", true) } == true && it.text != null
        }.take(4).joinToString("|") { it.text.toString().take(160) }
        val playbackFraction: Double? get() {
            for (node in nodes) {
                val id = node.viewIdResourceName.orEmpty().lowercase()
                val label = label(node)
                val isPlayback = node.className?.contains("SeekBar") == true ||
                    listOf("seek", "playback_progress", "video_progress", "player_progress", "reel_progress").any { id.contains(it) } ||
                    listOf("video progress", "playback progress", "seek control").any { label.startsWith(it) }
                if (!isPlayback) continue
                node.rangeInfo?.let { range ->
                    val span = range.max.toDouble() - range.min
                    val current = range.current.toDouble() - range.min
                    if (span.isFinite() && span > 0 && current.isFinite() && current >= 0 && current <= span) return current / span
                }
                PlaybackText.fraction(label)?.let { return it }
                PlaybackText.fraction(node.text?.toString().orEmpty())?.let { return it }
            }
            return null
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED && !gestureInFlight &&
            (event.className?.contains("RecyclerView") == true || event.className?.contains("ViewPager") == true)) resetProgress()
    }
    override fun onInterrupt() { pause("Accessibility interrupted — session paused") }
    private fun deviceReady(): Boolean = getSystemService(PowerManager::class.java).isInteractive &&
        !getSystemService(KeyguardManager::class.java).isKeyguardLocked
    override fun onDestroy() {
        connected = false
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(commands) }
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        prefs.edit().putBoolean(DoomSettings.SESSION, false).putString(DoomSettings.STATUS, "Accessibility service disconnected").apply()
        super.onDestroy()
    }
    @Suppress("DEPRECATION")
    private fun release(node: AccessibilityNodeInfo) { node.recycle() }
    companion object {
        private val SUPPORTED = setOf("com.google.android.youtube", "com.instagram.android", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill")
    }
}
