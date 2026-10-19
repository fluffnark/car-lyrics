package com.doomslug.carlyrics

import org.junit.Assert.*
import org.junit.Test

class MorpheResumePointTest {
    private val selected = KaraokeVideo("abcdefghijk", "Selected")
    private val autoplay = KaraokeVideo("12345678901", "Autoplay")
    @Test fun resolvedAutoplayKeepsItsOwnIdentityAndPausedState() {
        assertEquals(MorpheResumePoint(autoplay, 42_000, false),
            MorpheResumePoint.capture(selected, autoplay, VideoTimeline(42_000, 90_000, true), true))
    }
    @Test fun unknownAutoplayNeverAppliesItsTimestampToThePreviousSelection() {
        assertEquals(MorpheResumePoint(selected, 0, true),
            MorpheResumePoint.capture(selected, null, VideoTimeline(42_000, 90_000, true), false))
    }
    @Test fun endingAndMissingTimelinesRestartWithoutBorrowingAnInvalidPosition() {
        assertEquals(0L, MorpheResumePoint.capture(selected, selected, VideoTimeline(90_000, 90_000, true), false)!!.positionMs)
        assertEquals(0L, MorpheResumePoint.capture(selected, selected, null, false)!!.positionMs)
        assertNull(MorpheResumePoint.capture(null, null, null, true))
    }
}
