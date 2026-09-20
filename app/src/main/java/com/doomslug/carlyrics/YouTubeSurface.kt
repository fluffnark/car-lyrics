package com.doomslug.carlyrics

import android.app.Presentation
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.graphics.Color
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.Surface
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.car.app.SurfaceContainer

/** Renders the unmodified YouTube iframe through Android's virtual-display car surface path. */
class YouTubeSurface(private val context: Context) : VideoPlayer {
    /** A real HTTPS WebView origin gives YouTube an identifiable embedder referrer. */
    private companion object {
        const val WEB_ORIGIN = "https://appassets.androidplatform.net"
    }
    override var onStatus: ((PlaybackStatus) -> Unit)? = null
    private val main = Handler(Looper.getMainLooper())
    private var hostSurface: Surface? = null
    private var display: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var root: FrameLayout? = null
    private var webView: WebView? = null
    private var width = 0
    private var height = 0
    private var video: KaraokeVideo? = null
    private var authorized = false
    private var desiredPlaying = false
    @Volatile private var closed = false
    private var generation = 0
    private var status = PlaybackStatus.IDLE
    private var audioFocusRequest: AudioFocusRequest? = null
    private var watchPageFallback = false

    override fun onSurfaceAvailable(container: SurfaceContainer) {
        if (closed) return
        val surface = container.surface
        val w = container.width
        val h = container.height
        val dpi = container.dpi
        main.post {
            if (closed) return@post
            Log.i("CarLyricsPlayer", "surface available ${w}x${h} dpi=$dpi valid=${surface?.isValid}")
            releaseDisplay()
            hostSurface = surface
            width = w
            height = h
            if (surface?.isValid != true || w <= 0 || h <= 0) { update(PlaybackStatus.ERROR); return@post }
            runCatching {
                requestAudioFocus()
                display = context.getSystemService(DisplayManager::class.java)
                    // Presentation is required for a Presentation/WebView to render
                    // into a virtual display instead of leaving the host surface blank.
                    .createVirtualDisplay(
                        "Car Lyrics player",
                        w,
                        h,
                        dpi,
                        surface,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or
                            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
                    )
                presentation = Presentation(context, display!!.display)
                root = FrameLayout(presentation!!.context).apply { setBackgroundColor(Color.BLACK) }
                webView = WebView(presentation!!.context).apply {
                    setBackgroundColor(Color.BLACK)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            Log.i("CarLyricsPlayer", "page finished url=$url hardware=${view.isHardwareAccelerated}")
                            if (watchPageFallback && authorized && desiredPlaying) {
                                view.evaluateJavascript("document.querySelector('video')?.play();", null)
                                update(PlaybackStatus.PLAYING)
                            }
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                            val line = message.message()
                            if (line.startsWith("CAR_LYRICS")) Log.i("CarLyricsPlayer", line)
                            val marker = line.split(':', limit = 3)
                            if (marker.size == 3 && authorized && marker[1] == video?.id) when (marker[0]) {
                                "CAR_LYRICS_READY" -> {
                                    if (desiredPlaying) script("player.playVideo()") else update(PlaybackStatus.PAUSED)
                                }
                                "CAR_LYRICS_STATE" -> handlePlayerState(marker[2].toIntOrNull())
                                "CAR_LYRICS_ERROR" -> {
                                    val code = marker[2].toIntOrNull()
                                    Log.w("CarLyricsPlayer", "YouTube error ${marker[2]}")
                                    if ((code == 101 || code == 150) && !watchPageFallback) loadWatchPage(video!!)
                                    else update(PlaybackStatus.ERROR)
                                }
                            }
                            return true
                        }
                    }
                }
                root!!.addView(webView)
                presentation!!.setContentView(root!!)
                presentation!!.show()
                applyArea()
                if (authorized) video?.let(::load)
            }.onFailure {
                Log.e("CarLyricsPlayer", "Could not create car video surface", it)
                releaseDisplay()
                update(PlaybackStatus.ERROR)
            }
        }
    }

    override fun onSurfaceDestroyed(container: SurfaceContainer) {
        val surface = container.surface
        main.post { if (hostSurface == surface) { releaseDisplay(); hostSurface = null } }
    }

    // The player is a development video surface. Keep it edge-to-edge instead of
    // shrinking it to the host's map-safe visible rectangle (which leaves the map
    // exposed on the right side of wide Android Auto displays).
    override fun onVisibleAreaChanged(visibleArea: Rect) { main.post { applyArea() } }
    override fun onStableAreaChanged(stableArea: Rect) { main.post { applyArea() } }

    override fun select(video: KaraokeVideo) { main.post {
        this.video = video
        authorized = true
        desiredPlaying = true
        update(PlaybackStatus.LOADING)
        if (webView != null) load(video)
    } }

    override fun pause() { main.post {
        desiredPlaying = false
        if (watchPageFallback) webView?.evaluateJavascript("document.querySelector('video')?.pause();", null)
        else script("player.pauseVideo()")
    } }
    override fun resume() { main.post {
        if (!authorized) return@post
        desiredPlaying = true
        if (status == PlaybackStatus.ERROR) video?.let(::load)
        else if (watchPageFallback) webView?.evaluateJavascript("document.querySelector('video')?.play();", null)
        else script("player.playVideo()")
    } }
    override fun hide() { main.post {
        authorized = false
        desiredPlaying = false
        watchPageFallback = false
        generation++
        webView?.loadUrl("about:blank")
        abandonAudioFocus()
        update(PlaybackStatus.IDLE)
    } }
    override fun close() { closed = true; main.post { generation++; releaseDisplay(); onStatus = null } }

    private fun requestAudioFocus() {
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                .setWillPauseWhenDucked(false)
                .build()
            audioFocusRequest = request
            audio.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audio.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun abandonAudioFocus() {
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) audioFocusRequest?.let { audio.abandonAudioFocusRequest(it) }
        else {
            @Suppress("DEPRECATION")
            audio.abandonAudioFocus(null)
        }
        audioFocusRequest = null
    }

    private fun load(value: KaraokeVideo) {
        if (!authorized || !KaraokeVideo.ID.matches(value.id)) return
        watchPageFallback = false
        val ticket = ++generation
        update(PlaybackStatus.LOADING)
        val html = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1"><meta name="referrer" content="strict-origin-when-cross-origin"></head>
          <body style="margin:0;background:#000;overflow:hidden"><div id="player"></div>
          <script src="https://www.youtube.com/iframe_api"></script><script>
          var player;
          function onYouTubeIframeAPIReady() {
            player = new YT.Player('player', {
              width:'100%',height:'100%',videoId:'${value.id}',
              playerVars:{playsinline:1,controls:1,autoplay:0,enablejsapi:1,origin:'$WEB_ORIGIN'},
              events:{
                onReady:function(e){console.log('CAR_LYRICS_READY:${value.id}:1');},
                onStateChange:function(e){console.log('CAR_LYRICS_STATE:${value.id}:'+e.data);},
                onError:function(e){console.log('CAR_LYRICS_ERROR:${value.id}:'+e.data);}
              }
            });
          }
          </script><style>html,body,#player{width:100%;height:100%;}</style></body></html>"""
        // A real HTTPS base URL supplies the Referer required by YouTube's embedded player.
        webView?.loadDataWithBaseURL("$WEB_ORIGIN/", html, "text/html", "UTF-8", null)
        main.postDelayed({
            if (ticket == generation && authorized && status == PlaybackStatus.LOADING) update(PlaybackStatus.ERROR)
        }, 20_000L)
    }

    /** Error 101/150 means the owner disallows embedding; try YouTube's own watch page. */
    private fun loadWatchPage(value: KaraokeVideo) {
        if (!authorized || !KaraokeVideo.ID.matches(value.id)) return
        watchPageFallback = true
        val ticket = ++generation
        update(PlaybackStatus.LOADING)
        webView?.loadUrl("https://www.youtube.com/watch?v=${value.id}&autoplay=1")
        main.postDelayed({
            if (ticket == generation && authorized && status == PlaybackStatus.LOADING) update(PlaybackStatus.ERROR)
        }, 20_000L)
    }

    private fun handlePlayerState(code: Int?) {
        if (!authorized) return
        when (code) {
            0 -> update(PlaybackStatus.ENDED)
            1 -> if (desiredPlaying) update(PlaybackStatus.PLAYING) else script("player.pauseVideo()")
            2 -> update(PlaybackStatus.PAUSED)
            3 -> update(PlaybackStatus.LOADING)
            5 -> if (!desiredPlaying) update(PlaybackStatus.PAUSED)
        }
    }

    private fun script(command: String) {
        webView?.evaluateJavascript("if (typeof player !== 'undefined' && player) {$command;}", null)
    }

    private fun update(next: PlaybackStatus) {
        if (status != next) { status = next; onStatus?.invoke(next) }
    }

    private fun applyArea() {
        val view = webView ?: return
        view.layoutParams = FrameLayout.LayoutParams(width.coerceAtLeast(1), height.coerceAtLeast(1), Gravity.TOP or Gravity.LEFT)
    }

    private fun releaseDisplay() {
        abandonAudioFocus()
        webView?.loadUrl("about:blank")
        webView?.let { root?.removeView(it) }
        webView?.destroy()
        webView = null
        presentation?.dismiss()
        presentation = null
        root = null
        display?.release()
        display = null
    }
}
