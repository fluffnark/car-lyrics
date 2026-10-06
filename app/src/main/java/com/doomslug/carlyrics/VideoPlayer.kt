package com.doomslug.carlyrics

import androidx.car.app.SurfaceCallback

enum class PlaybackStatus { IDLE, LOADING, PLAYING, PAUSED, ENDED, ERROR }
data class VideoTimeline(val positionMs: Long, val durationMs: Long, val seekable: Boolean)

interface VideoPlayer : SurfaceCallback {
    var onStatus: ((PlaybackStatus) -> Unit)?
    val statusDetail: String? get() = null
    val compactControls: Boolean get() = false
    val timeline: VideoTimeline? get() = null
    fun seekTo(positionMs: Long) = Unit
    fun recoverVideo() = Unit
    fun prepare() = Unit
    fun setCompactHeader(enabled: Boolean) = Unit
    fun videoToSave(selected: KaraokeVideo?): KaraokeVideo? = selected
    fun select(video: KaraokeVideo)
    fun pause()
    fun resume()
    fun hide()
    fun close()
}
