package com.doomslug.carlyrics

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Parcel
import android.os.Process
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShizukuConnectionTest {
    @Test fun reconnectTargetsOnlyShizukuAndRejectsUntrustedBinderCallbacks() {
        val app = RuntimeEnvironment.getApplication()
        val manager = "moe.shizuku.privileged.api"
        shadowOf(app.packageManager).installPackage(PackageInfo().apply {
            packageName = manager
            applicationInfo = ApplicationInfo().apply { packageName = manager; uid = Process.myUid() + 500 }
        })
        val before = shadowOf(app).broadcastIntents.size
        ShizukuConnection.refresh(app)
        val request = shadowOf(app).broadcastIntents.last()
        assertEquals("rikka.shizuku.intent.action.REQUEST_BINDER", request.action)
        assertEquals(ComponentName(manager, "moe.shizuku.manager.receiver.ShizukuReceiver"), request.component)
        val callback = request.getBundleExtra("data")!!.getBinder("binder")!!
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            // A different UID cannot submit a server binder; rejection happens before parsing.
            assertFalse(callback.transact(1, data, reply, 0))
            assertFalse(callback.transact(2, data, reply, 0))
        } finally { data.recycle(); reply.recycle() }
        ShizukuConnection.refresh(app)
        assertEquals(before + 1, shadowOf(app).broadcastIntents.size)
        assertFalse(MorpheNativeDisplay.running())
    }
}
