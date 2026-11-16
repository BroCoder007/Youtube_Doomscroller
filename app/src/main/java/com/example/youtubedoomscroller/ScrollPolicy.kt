package com.example.youtubedoomscroller

import java.util.Locale

enum class VoiceCommand { PAUSE, STOP, RESUME, NEXT, PREVIOUS, LIKE, COMMENT, SMART_MODE, TIMER_MODE }

/** Deliberately small grammar: a transcript can produce at most one command. */
object CommandParser {
    fun parse(transcript: String, requireWakeWord: Boolean = true): VoiceCommand? {
        var phrase = transcript.lowercase(Locale.ROOT).replace(Regex("[^a-z ]"), " ")
            .replace(Regex("\\s+"), " ").trim()
        val hasWakeWord = phrase.startsWith("scroller ")
        if (requireWakeWord && !hasWakeWord) return null
        if (hasWakeWord) phrase = phrase.removePrefix("scroller ")
        return when (phrase) {
            "pause", "pause scrolling" -> VoiceCommand.PAUSE
            "stop", "stop scrolling" -> VoiceCommand.STOP
            "resume", "start", "resume scrolling" -> VoiceCommand.RESUME
            "next", "next video", "skip" -> VoiceCommand.NEXT
            "previous", "previous video", "go back" -> VoiceCommand.PREVIOUS
            "like", "like video", "like this video" -> VoiceCommand.LIKE
            "comment", "comments", "open comments" -> VoiceCommand.COMMENT
            "smart mode", "enable smart mode" -> VoiceCommand.SMART_MODE
            "timer mode", "enable timer mode" -> VoiceCommand.TIMER_MODE
            else -> null
        }
    }
}

data class PlaybackSample(val key: String, val fraction: Double, val observedAtMs: Long)

/** Completes only at the observed end or a near-end to beginning loop, never on a timer. */
class CompletionTracker {
    private var previous: PlaybackSample? = null
    private var progressed = false
    private var consumed = false
    private var firstObservedAt = 0L
    private var lastForwardRate: Double? = null

    fun reset() { previous = null; progressed = false; consumed = false; firstObservedAt = 0L; lastForwardRate = null }

    fun observe(sample: PlaybackSample): Boolean {
        if (!sample.fraction.isFinite() || sample.fraction !in 0.0..1.0) { reset(); return false }
        val old = previous
        if (old == null || old.key != sample.key || sample.observedAtMs - old.observedAtMs > 2_000L || sample.observedAtMs <= old.observedAtMs) {
            previous = sample; progressed = false; consumed = false
            firstObservedAt = sample.observedAtMs
            lastForwardRate = null
            return false
        }
        val end = sample.fraction >= 1.0 && old.fraction < 1.0
        val loop = old.fraction >= 0.95 && sample.fraction <= 0.05
        val step = if (loop) 1.0 - old.fraction + sample.fraction else sample.fraction - old.fraction
        val elapsedSeconds = (sample.observedAtMs - old.observedAtMs) / 1_000.0
        val allowedStep = minOf(0.25, maxOf(0.12, (lastForwardRate ?: 0.1) * elapsedSeconds * 3.0))
        // Seeking and replacing a player often produce abrupt jumps. Require fresh evidence afterward.
        val implausibleLoop = loop && step > maxOf(0.02, (lastForwardRate ?: 0.0) * elapsedSeconds * 3.0)
        if (step < -0.001 || step > allowedStep || implausibleLoop) {
            previous = sample; progressed = false; consumed = false
            firstObservedAt = sample.observedAtMs; lastForwardRate = null
            return false
        }
        if (step > 0.001 && !loop) {
            progressed = true
            lastForwardRate = step / elapsedSeconds
        }
        previous = sample
        if (!consumed && progressed && sample.observedAtMs - firstObservedAt >= 500L && (end || loop)) {
            consumed = true
            return true
        }
        return false
    }
}

object PlaybackText {
    // Only explicit paired timestamps are accepted; likes, captions and countdowns are ignored.
    private val pattern = Regex("(?<![\\d:])(\\d{1,2}:\\d{2}(?::\\d{2})?)\\s*(?:/|of)\\s*(\\d{1,2}:\\d{2}(?::\\d{2})?)(?![\\d:])", RegexOption.IGNORE_CASE)
    fun fraction(text: String): Double? {
        val match = pattern.find(text) ?: return null
        fun seconds(value: String): Long? {
            val parts = value.split(':').map { it.toLongOrNull() ?: return null }
            if (parts.drop(1).any { it >= 60L }) return null
            return parts.fold(0L) { sum, n -> sum * 60 + n }
        }
        val current = seconds(match.groupValues[1]) ?: return null
        val total = seconds(match.groupValues[2]) ?: return null
        if (total <= 0 || current > total) return null
        return current.toDouble() / total
    }
}
