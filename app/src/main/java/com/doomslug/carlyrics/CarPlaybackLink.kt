package com.doomslug.carlyrics

/** In-process phone shortcut; no broadcast or exported control endpoint. */
internal object CarPlaybackLink {
    var playVideo: ((KaraokeVideo) -> Boolean)? = null
    var showPlayer: (() -> Boolean)? = null
}
