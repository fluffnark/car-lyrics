package com.doomslug.carlyrics

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcel
import android.os.SystemClock
import rikka.shizuku.Shizuku

/** Re-request the live server from Shizuku's own receiver if its startup delivery was missed.
 * The manager's shell-client protocol is available in installed Shizuku 13.6.0.
 * This attaches this package only; normal Shizuku authorization is still required.
 */
internal object ShizukuConnection {
    private const val MANAGER = "moe.shizuku.privileged.api"
    private val main = Handler(Looper.getMainLooper())
    private var lastRequest = -10_000L

    // Audited against Shizuku 13.6.0 ShellBinderRequestHandler. The SDK marks
    // provider attachment internal, so keep this compatibility fallback isolated.
    @android.annotation.SuppressLint("RestrictedApi")
    fun refresh(context: Context) {
        if (MorpheNativeDisplay.running()) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastRequest < 2_000) return
        val app = context.applicationContext
        val managerUid = runCatching { app.packageManager.getApplicationInfo(MANAGER, 0).uid }.getOrNull() ?: return
        lastRequest = now
        val callback = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code != 1 || getCallingUid() != managerUid) return false
                val binder = data.readStrongBinder() ?: return true
                main.post {
                    if (!MorpheNativeDisplay.running()) runCatching { Shizuku.onBinderReceived(binder, app.packageName) }
                }
                return true
            }
        }
        runCatching {
            app.sendBroadcast(Intent("rikka.shizuku.intent.action.REQUEST_BINDER")
                .setComponent(ComponentName(MANAGER, "moe.shizuku.manager.receiver.ShizukuReceiver"))
                .putExtra("data", Bundle().apply { putBinder("binder", callback) }))
        }
    }
}
