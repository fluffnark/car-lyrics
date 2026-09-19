package com.doomslug.carlyrics

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** A companion status screen. The car flow requires no phone interaction. */
class MainActivity : Activity() {
    private val ink = Color.rgb(240, 247, 248)
    private val muted = Color.rgb(158, 178, 188)
    private val mint = Color.rgb(121, 229, 213)
    private val gold = Color.rgb(245, 206, 130)
    private lateinit var catalogStatus: TextView
    private lateinit var preview: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(13, 20, 32))
            isFillViewport = true
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(36))
        }
        body.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            view.setPadding(dp(24), dp(28) + bars.top, dp(24), dp(36) + bars.bottom)
            insets
        }
        scroll.addView(body)

        body.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher)
            layoutParams = LinearLayout.LayoutParams(dp(72), dp(72))
        })
        body.addView(space(24))
        body.addView(text("CAR LYRICS", 13f, mint, true).apply { letterSpacing = 0.18f })
        body.addView(space(8))
        body.addView(text("Your parked karaoke stage.", 36f, ink, true))
        body.addView(space(12))
        body.addView(text("Choose a Sing King song with your Mazda Commander knob while parked. Your phone stays in your pocket.", 17f, muted))
        body.addView(space(28))

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = panel(Color.rgb(28, 44, 57), 20)
        }
        card.addView(text("ON YOUR MAZDA DISPLAY", 12f, mint, true).apply { letterSpacing = 0.12f })
        card.addView(space(12))
        card.addView(text("Connect your Pixel, then open Car Lyrics on the Mazda display.", 20f, ink, true))
        card.addView(space(8))
        card.addView(text("Browse, play, save, and skip from the car screen while parked.", 15f, muted))
        body.addView(card)
        body.addView(space(30))
        body.addView(text("SING KING • RECENT", 13f, gold, true).apply { letterSpacing = 0.12f })
        body.addView(space(10))
        catalogStatus = text("Checking the channel…", 15f, muted)
        body.addView(catalogStatus)
        preview = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(preview)
        body.addView(space(30))
        body.addView(text("Video appears only after you select a song while parked. Some YouTube uploads may block embedding or show ads.", 14f, muted))
        setContentView(scroll)

        SingKingCatalog.initialize(this)
        updateCatalog()
        if (SingKingCatalog.videos.isEmpty() || SingKingCatalog.isStale()) SingKingCatalog.refresh { updateCatalog() }
    }

    private fun updateCatalog() {
        val videos = SingKingCatalog.videos
        catalogStatus.text = when {
            videos.isNotEmpty() && SingKingCatalog.error != null -> "${videos.size} cached songs • Refresh unavailable"
            videos.isNotEmpty() -> "${videos.size} recent karaoke videos available"
            SingKingCatalog.loading -> "Checking the channel…"
            else -> SingKingCatalog.error ?: "Waiting for channel videos"
        }
        preview.removeAllViews()
        videos.take(3).forEachIndexed { index, video ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(15), dp(13), dp(15), dp(13))
                background = panel(Color.rgb(24, 37, 50), 14)
            }
            row.addView(text("%02d".format(index + 1), 15f, mint, true),
                LinearLayout.LayoutParams(dp(34), ViewGroup.LayoutParams.WRAP_CONTENT))
            row.addView(text(video.title, 15f, ink),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            preview.addView(space(8))
            preview.addView(row)
        }
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private fun space(height: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun panel(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
}
