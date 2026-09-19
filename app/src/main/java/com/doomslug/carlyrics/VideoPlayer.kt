package com.doomslug.carlyrics

import androidx.car.app.SurfaceCallback

enum class PlaybackStatus { IDLE, LOADING, PLAYING, PAUSED, ENDED, ERROR }

interface VideoPlayer : SurfaceCallback {
    var onStatus: ((PlaybackStatus) -> Unit)?
    fun select(video: KaraokeVideo)
    fun pause()
    fun resume()
    fun hide()
    fun close()
}
