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
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer

/** Displays the official YouTube iframe player in a View on the host's surface. */
class YouTubeSurface(private val context: Context) : SurfaceCallback {
    private val main = Handler(Looper.getMainLooper())
    private var display: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var root: FrameLayout? = null
    private var webView: WebView? = null
    private var area: Rect? = null
    private var width = 0
    private var height = 0
    private var video: SingKingCatalog.Video? = null
    private var authorized = false

    override fun onSurfaceAvailable(container: SurfaceContainer) {
        val surface = container.surface
        val w = container.width
        val h = container.height
        val dpi = container.dpi
        main.post {
            releaseDisplay()
            width = w
            height = h
            display = context.getSystemService(DisplayManager::class.java)
                .createVirtualDisplay("Car Lyrics player", w, h, dpi, surface, 0)
            presentation = Presentation(context, display!!.display)
            root = FrameLayout(presentation!!.context).apply { setBackgroundColor(Color.BLACK) }
            webView = WebView(presentation!!.context).apply {
                setBackgroundColor(Color.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                        Log.d("CarLyricsPlayer", message.message())
                        return true
                    }
                }
            }
            root!!.addView(webView)
            presentation!!.setContentView(root!!)
            presentation!!.show()
            applyArea()
            if (authorized) video?.let(::load)
        }
    }

    override fun onSurfaceDestroyed(container: SurfaceContainer) { main.post { releaseDisplay() } }
    override fun onVisibleAreaChanged(visibleArea: Rect) { main.post { area = Rect(visibleArea); applyArea() } }
    override fun onStableAreaChanged(stableArea: Rect) { main.post { area = Rect(stableArea); applyArea() } }

    fun select(value: SingKingCatalog.Video) = main.post {
        video = value
        authorized = true
        if (webView != null) load(value)
    }

    fun pause() = main.post { script("player && player.pauseVideo()") }
    fun resume() = main.post { if (authorized) script("player && player.playVideo()") }
    fun hide() = main.post {
        authorized = false
        webView?.loadUrl("about:blank")
    }
    fun close() = main.post { releaseDisplay() }

    private fun load(value: SingKingCatalog.Video) {
        if (!authorized || !value.id.matches(Regex("[A-Za-z0-9_-]{11}"))) return
        val html = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1"></head>
            <body style="margin:0;background:#000;overflow:hidden"><div id="player"></div>
            <script src="https://www.youtube.com/iframe_api"></script><script>
            var player;
            function onYouTubeIframeAPIReady() {
              player = new YT.Player('player', {
                width:'100%',height:'100%',videoId:'${value.id}',
                playerVars:{playsinline:1,controls:1,autoplay:0},
                events:{onReady:function(e){e.target.playVideo();},onError:function(e){console.log('YouTube player error '+e.data);}}
              });
            }
            </script><style>html,body,#player{width:100%;height:100%;}</style></body></html>"""
        // YouTube requires the installed app ID as the embedded player's Referer.
        webView?.loadDataWithBaseURL("https://com.doomslug.carlyrics", html, "text/html", "UTF-8", null)
    }

    private fun script(command: String) { webView?.evaluateJavascript("if (typeof player !== 'undefined') {$command;}", null) }

    private fun applyArea() {
        val view = webView ?: return
        val bounds = area?.takeIf { !it.isEmpty } ?: Rect(0, 0, width, height)
        view.layoutParams = FrameLayout.LayoutParams(bounds.width().coerceAtLeast(1), bounds.height().coerceAtLeast(1), Gravity.TOP or Gravity.LEFT)
            .apply { leftMargin = bounds.left; topMargin = bounds.top }
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
