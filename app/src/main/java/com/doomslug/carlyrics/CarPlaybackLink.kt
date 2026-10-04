package com.doomslug.carlyrics

/** In-process phone shortcut; no broadcast or exported control endpoint. */
internal object CarPlaybackLink {
    var showPlayer: (() -> Boolean)? = null
}
