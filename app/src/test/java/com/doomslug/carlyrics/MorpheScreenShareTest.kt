package com.doomslug.carlyrics

import android.app.NotificationManager
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import android.view.Display
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MorpheScreenShareTest {
    @Test fun songOpensOnPhoneRatherThanPrivateCarDisplay() {
        val app = RuntimeEnvironment.getApplication()
        val carDisplayContext = object : ContextWrapper(app) {
            override fun startActivity(intent: Intent, options: Bundle?) {
                fail("Must not launch using the car display context")
            }
        }
        shadowOf(app.getSystemService(NotificationManager::class.java))
            .setNotificationListenerAccessGranted(MorpheMediaAccess.component(app), true)
        MorpheCaptureGrant.update(true, "Ready")
        val player = MorpheScreenShare(carDisplayContext)
        try {
            player.select(KaraokeVideo("0bWyV22Gzhw", "Test karaoke"))
            val launched = shadowOf(app).nextStartedActivityForResult
            assertEquals(MorpheMediaAccess.PACKAGE, launched.intent.`package`)
            assertEquals("https://www.youtube.com/watch?v=0bWyV22Gzhw", launched.intent.dataString)
            assertEquals(Display.DEFAULT_DISPLAY, launched.options.getInt("android.activity.launchDisplayId", -1))
        } finally {
            player.close()
            MorpheCaptureGrant.update(false, "Stopped")
        }
    }
}
