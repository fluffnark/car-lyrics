package com.doomslug.carlyrics

import android.content.Intent

/** Process-local MediaProjection consent. Android invalidates this after a process/device reset. */
object MorpheCaptureGrant {
    var resultCode: Int = 0
    var data: Intent? = null

    val isGranted: Boolean
        get() = resultCode != 0 && data != null

    fun clear() {
        resultCode = 0
        data = null
    }
}
