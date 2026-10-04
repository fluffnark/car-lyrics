package com.doomslug.carlyrics

import android.graphics.Rect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VideoViewportTest {
    @Test fun dashboardMediaCardCannotCoverTheVideo() {
        // The host still supplies a full-width surface while its right card overlays it.
        val area = Rect(0, 0, 770, 460)
        val fit = VideoViewport.fit(1190, 460, 1920, 1080, area)
        assertTrue(area.contains(fit))
        assertEquals(770, fit.width())
        assertEquals(433, fit.height())
        assertEquals(Rect(0, 0, 770, 460), area)
    }

    @Test fun leftPanelAndTopControlsUseSurfaceCoordinates() {
        val area = Rect(420, 50, 1190, 400)
        val fit = VideoViewport.fit(1190, 460, 1920, 1080, area)
        assertTrue(area.contains(fit))
        assertEquals(350, fit.height())
        assertEquals(50, fit.top)
        assertEquals(400, fit.bottom)
    }

    @Test fun wideViewExpandsAndUnknownAreaUsesWholeSurface() {
        val full = VideoViewport.fit(1190, 460, 1920, 1080, null)
        assertEquals(460, full.height())
        assertEquals(817, full.width())
        assertEquals(full, VideoViewport.fit(1190, 460, 1920, 1080, Rect()))
        assertEquals(full, VideoViewport.fit(1190, 460, 1920, 1080, Rect(-30, -20, 1300, 900)))
        assertTrue(VideoViewport.fit(1190, 460, 0, 1080, null).isEmpty)
        assertTrue(VideoViewport.fit(1190, 460, 1920, 1080, Rect(1200, 0, 1500, 400)).isEmpty)
    }
}
