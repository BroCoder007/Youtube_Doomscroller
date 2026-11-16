package com.example.youtubedoomscroller

import org.junit.Assert.*
import org.junit.Test

class ScrollPolicyTest {
    @Test fun wakeWordIsRequiredByDefault() {
        assertNull(CommandParser.parse("next"))
        assertEquals(VoiceCommand.NEXT, CommandParser.parse("Scroller, next!"))
        assertEquals(VoiceCommand.NEXT, CommandParser.parse("next", false))
    }
    @Test fun ignoresSimilarWordsAndConversationalSpeech() {
        listOf("scroller dislike", "scroller unlike", "scroller I like this", "I like this video", "scroller next and like", "scroller stoplight")
            .forEach { assertNull(it, CommandParser.parse(it)) }
    }
    @Test fun allCommandsHaveExactGrammar() {
        mapOf("pause" to VoiceCommand.PAUSE, "stop" to VoiceCommand.STOP, "resume" to VoiceCommand.RESUME,
            "next" to VoiceCommand.NEXT, "previous" to VoiceCommand.PREVIOUS, "like" to VoiceCommand.LIKE,
            "comments" to VoiceCommand.COMMENT, "smart mode" to VoiceCommand.SMART_MODE,
            "timer mode" to VoiceCommand.TIMER_MODE).forEach { (phrase, command) ->
            assertEquals(command, CommandParser.parse("scroller $phrase"))
        }
    }
    @Test fun parsesExplicitPlaybackTimes() {
        assertEquals(0.5, PlaybackText.fraction("Playback 00:15 / 00:30")!!, 0.0001)
        assertEquals(0.5, PlaybackText.fraction("1:00:00 of 2:00:00")!!, 0.0001)
        assertEquals(1.0, PlaybackText.fraction("00:30 / 00:30")!!, 0.0001)
    }
    @Test fun rejectsInvalidOrUnrelatedPlaybackText() {
        listOf("30 likes", "00:31 / 00:30", "00:00 / 00:00", "00:99 / 01:00", "20 seconds remaining", "12:30")
            .forEach { assertNull(it, PlaybackText.fraction(it)) }
    }
    @Test fun neverCompletesMerelyBecauseTimePasses() {
        val tracker = CompletionTracker()
        repeat(300) { assertFalse(tracker.observe(PlaybackSample("a", 0.5, it * 400L))) }
    }
    @Test fun completesAtObservedEndOnlyOnce() {
        val tracker = CompletionTracker()
        assertFalse(tracker.observe(PlaybackSample("a", 0.85, 0)))
        assertFalse(tracker.observe(PlaybackSample("a", 0.95, 400)))
        assertTrue(tracker.observe(PlaybackSample("a", 1.0, 800)))
        assertFalse(tracker.observe(PlaybackSample("a", 1.0, 1_200)))
    }
    @Test fun loopFromObservedNearEndCompletes() {
        val tracker = CompletionTracker()
        assertFalse(tracker.observe(PlaybackSample("a", 0.90, 0)))
        assertFalse(tracker.observe(PlaybackSample("a", 0.98, 400)))
        assertTrue(tracker.observe(PlaybackSample("a", 0.01, 800)))
    }
    @Test fun rewindFromMiddleIsNotCompletion() {
        val tracker = CompletionTracker()
        tracker.observe(PlaybackSample("a", 0.5, 0))
        tracker.observe(PlaybackSample("a", 0.6, 400))
        assertFalse(tracker.observe(PlaybackSample("a", 0.01, 800)))
    }
    @Test fun newVideoOrObservationGapCannotCompleteOldVideo() {
        val tracker = CompletionTracker()
        tracker.observe(PlaybackSample("a", 0.8, 0))
        tracker.observe(PlaybackSample("a", 0.98, 400))
        assertFalse(tracker.observe(PlaybackSample("b", 0.01, 800)))
        tracker.observe(PlaybackSample("b", 0.98, 1_200))
        assertFalse(tracker.observe(PlaybackSample("b", 0.01, 4_000)))
    }
    @Test fun staleCompleteAndInvalidSamplesDoNotAdvance() {
        val tracker = CompletionTracker()
        assertFalse(tracker.observe(PlaybackSample("a", 1.0, 0)))
        assertFalse(tracker.observe(PlaybackSample("a", 1.0, 800)))
        assertFalse(tracker.observe(PlaybackSample("a", Double.NaN, 1_200)))
        assertFalse(tracker.observe(PlaybackSample("a", -0.1, 1_600)))
    }
    @Test fun resetRequiresFreshProgress() {
        val tracker = CompletionTracker()
        tracker.observe(PlaybackSample("a", 0.9, 0))
        tracker.observe(PlaybackSample("a", 0.99, 400))
        tracker.reset()
        assertFalse(tracker.observe(PlaybackSample("a", 0.01, 800)))
    }
    @Test fun seekingToEndOrImpossibleLoopRateDoesNotComplete() {
        val tracker = CompletionTracker()
        tracker.observe(PlaybackSample("a", 0.2, 0))
        tracker.observe(PlaybackSample("a", 0.21, 400))
        assertFalse(tracker.observe(PlaybackSample("a", 1.0, 800)))
        tracker.reset()
        tracker.observe(PlaybackSample("a", 0.94, 0))
        tracker.observe(PlaybackSample("a", 0.945, 400))
        tracker.observe(PlaybackSample("a", 0.951, 800))
        assertFalse(tracker.observe(PlaybackSample("a", 0.05, 801)))
    }
}
