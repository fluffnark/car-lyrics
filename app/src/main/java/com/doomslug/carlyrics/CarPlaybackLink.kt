package com.doomslug.carlyrics

/** In-process phone shortcut; no broadcast or exported control endpoint. */
internal object CarPlaybackLink {
    var playVideo: ((KaraokeVideo) -> Boolean)? = null
    var resumeVideo: ((KaraokeVideo, Long) -> Boolean)? = null
    var currentVideo: (() -> KaraokeVideo?)? = null
    var playNext: ((KaraokeVideo) -> Boolean)? = null
    var loadHome: ((Boolean, (String?, String?) -> Unit) -> Unit)? = null
    var repair: (() -> Boolean)? = null
    var showPlayer: (() -> Boolean)? = null
}
