package com.doomslug.carlyrics

import android.app.Presentation
import android.content.Context
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
    override var onStatus: ((PlaybackStatus) -> Unit)? = null
    private val main = Handler(Looper.getMainLooper())
    private var hostSurface: Surface? = null
    private var display: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var root: FrameLayout? = null
    private var webView: WebView? = null
    private var area: Rect? = null
    private var width = 0
    private var height = 0
    private var video: KaraokeVideo? = null
    private var authorized = false
    private var desiredPlaying = false
    @Volatile private var closed = false
    private var generation = 0
    private var status = PlaybackStatus.IDLE

    override fun onSurfaceAvailable(container: SurfaceContainer) {
        if (closed) return
        val surface = container.surface
        val w = container.width
        val h = container.height
        val dpi = container.dpi
        main.post {
            if (closed) return@post
            releaseDisplay()
            hostSurface = surface
            width = w
            height = h
            if (surface?.isValid != true || w <= 0 || h <= 0) { update(PlaybackStatus.ERROR); return@post }
            runCatching {
                display = context.getSystemService(DisplayManager::class.java)
                    .createVirtualDisplay("Car Lyrics player", w, h, dpi, surface, 0)
                presentation = Presentation(context, display!!.display)
                root = FrameLayout(presentation!!.context).apply { setBackgroundColor(Color.BLACK) }
                webView = WebView(presentation!!.context).apply {
                    setBackgroundColor(Color.BLACK)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    webViewClient = object : WebViewClient() {}
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                            val line = message.message()
                            val marker = line.split(':', limit = 3)
                            if (marker.size == 3 && authorized && marker[1] == video?.id) when (marker[0]) {
                                "CAR_LYRICS_READY" -> {
                                    if (desiredPlaying) script("player.playVideo()") else update(PlaybackStatus.PAUSED)
                                }
                                "CAR_LYRICS_STATE" -> handlePlayerState(marker[2].toIntOrNull())
                                "CAR_LYRICS_ERROR" -> {
                                    Log.w("CarLyricsPlayer", "YouTube error ${marker[2]}")
                                    update(PlaybackStatus.ERROR)
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

    override fun onVisibleAreaChanged(visibleArea: Rect) { main.post { area = Rect(visibleArea); applyArea() } }
    override fun onStableAreaChanged(stableArea: Rect) { main.post { area = Rect(stableArea); applyArea() } }

    override fun select(video: KaraokeVideo) { main.post {
        this.video = video
        authorized = true
        desiredPlaying = true
        update(PlaybackStatus.LOADING)
        if (webView != null) load(video)
    } }

    override fun pause() { main.post { desiredPlaying = false; script("player.pauseVideo()") } }
    override fun resume() { main.post {
        if (!authorized) return@post
        desiredPlaying = true
        if (status == PlaybackStatus.ERROR) video?.let(::load) else script("player.playVideo()")
    } }
    override fun hide() { main.post {
        authorized = false
        desiredPlaying = false
        generation++
        webView?.loadUrl("about:blank")
        update(PlaybackStatus.IDLE)
    } }
    override fun close() { closed = true; main.post { generation++; releaseDisplay(); onStatus = null } }

    private fun load(value: KaraokeVideo) {
        if (!authorized || !KaraokeVideo.ID.matches(value.id)) return
        val ticket = ++generation
        update(PlaybackStatus.LOADING)
        val html = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1"></head>
          <body style="margin:0;background:#000;overflow:hidden"><div id="player"></div>
          <script src="https://www.youtube.com/iframe_api"></script><script>
          var player;
          function onYouTubeIframeAPIReady() {
            player = new YT.Player('player', {
              width:'100%',height:'100%',videoId:'${value.id}',
              playerVars:{playsinline:1,controls:1,autoplay:0,origin:'https://com.doomslug.carlyrics'},
              events:{
                onReady:function(e){console.log('CAR_LYRICS_READY:${value.id}:1');},
                onStateChange:function(e){console.log('CAR_LYRICS_STATE:${value.id}:'+e.data);},
                onError:function(e){console.log('CAR_LYRICS_ERROR:${value.id}:'+e.data);}
              }
            });
          }
          </script><style>html,body,#player{width:100%;height:100%;}</style></body></html>"""
        // The installed app ID in baseUrl supplies YouTube's required HTTP Referer.
        webView?.loadDataWithBaseURL("https://com.doomslug.carlyrics", html, "text/html", "UTF-8", null)
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
        val region = area?.takeIf { !it.isEmpty } ?: Rect(0, 0, width, height)
        val left = region.left.coerceIn(0, width)
        val top = region.top.coerceIn(0, height)
        val right = region.right.coerceIn(left, width)
        val bottom = region.bottom.coerceIn(top, height)
        view.layoutParams = FrameLayout.LayoutParams((right - left).coerceAtLeast(1), (bottom - top).coerceAtLeast(1), Gravity.TOP or Gravity.LEFT)
            .apply { leftMargin = left; topMargin = top }
    }

    private fun releaseDisplay() {
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
