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
    @Test fun mazdaSplitPlayerUsesTheSpaceBelowItsCompactHeader() {
        val visible = Rect(440, 92, 678, 448)
        val old = VideoViewport.fit(770, 460, 1280, 720, visible)
        val fit = VideoViewport.fitCompactHeader(770, 460, 1280, 720, visible, 160)
        assertEquals(238, old.width())
        assertEquals(597, fit.width())
        assertEquals(336, fit.height())
        assertTrue(Rect(12, 112, 678, 448).contains(fit))
        assertEquals(Rect(440, 92, 678, 448), visible)
    }

    @Test fun compactHeaderRespectsMediaCardOnAFullWidthSurface() {
        val split = VideoViewport.fitCompactHeader(1190, 460, 1280, 720, Rect(440, 92, 678, 448), 160)
        assertTrue(split.right <= 678)
        val full = VideoViewport.fitCompactHeader(1190, 460, 1280, 720, Rect(440, 92, 1098, 448), 160)
        assertTrue(full.width() > split.width())
        assertTrue(Rect(440, 92, 1098, 448).contains(full))
    }

    @Test fun compactHeaderScalesWithDensityAndFallsBackWhenGeometryIsUnknown() {
        val doubled = VideoViewport.fitCompactHeader(1540, 920, 1280, 720, Rect(880, 184, 1356, 896), 320)
        assertTrue(Rect(24, 224, 1356, 896).contains(doubled))
        listOf(null, Rect(), Rect(0, 0, 770, 460), Rect(1200, 0, 1500, 400)).forEach {
            assertEquals(VideoViewport.fit(1190, 460, 1280, 720, it),
                VideoViewport.fitCompactHeader(1190, 460, 1280, 720, it, 160))
        }
        val short = Rect(440, 92, 678, 98)
        assertEquals(VideoViewport.fit(770, 100, 1280, 720, short),
            VideoViewport.fitCompactHeader(770, 100, 1280, 720, short, 160))
    }

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
