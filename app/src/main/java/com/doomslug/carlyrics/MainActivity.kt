package com.doomslug.carlyrics

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.media.projection.MediaProjectionManager

/** A companion status screen. The car flow requires no phone interaction. */
class MainActivity : Activity() {
    private val ink = Color.rgb(240, 247, 248)
    private val muted = Color.rgb(158, 178, 188)
    private val mint = Color.rgb(121, 229, 213)
    private val gold = Color.rgb(245, 206, 130)
    private lateinit var catalogStatus: TextView
    private lateinit var preview: LinearLayout
    private lateinit var phoneResults: LinearLayout
    private lateinit var queueStatus: TextView
    private val queueStore by lazy { QueueStore(this) }
    private val playlistStore by lazy { PlaylistStore(this) }
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchGeneration = 0
    private var remoteResults = emptyList<KaraokeVideo>()
    private lateinit var morpheStatus: TextView

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
        body.addView(text("Your karaoke stage.", 36f, ink, true))
        body.addView(space(12))
        body.addView(text("Choose a Sing King song with your Mazda Commander knob. Your phone stays in your pocket.", 17f, muted))
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
        card.addView(text("Browse, play, save, and skip from the car screen.", 15f, muted))
        body.addView(card)
        body.addView(space(14))
        val morpheCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = panel(Color.rgb(35, 42, 61), 20)
        }
        morpheCard.addView(text("EXPERIMENTAL MORPHE MIRROR", 12f, gold, true).apply { letterSpacing = 0.10f })
        morpheCard.addView(space(8))
        morpheStatus = text("Approve screen share, select Morphe in the picker, then use the Mazda screen and knob.", 14f, muted)
        morpheCard.addView(morpheStatus)
        morpheCard.addView(space(10))
        morpheCard.addView(Button(this).apply {
            text = "Enable Morphe screen share"
            isAllCaps = false
            setOnClickListener { requestMorpheCapture() }
        })
        body.addView(morpheCard)
        body.addView(space(30))
        body.addView(text("PASSENGER QUEUE", 13f, mint, true).apply { letterSpacing = 0.14f })
        body.addView(space(8))
        body.addView(text("Search and add songs from the phone while the car screen stays focused on playback.", 15f, muted))
        body.addView(space(10))
        val search = EditText(this).apply {
            hint = "Search song, artist, album, or genre"
            setSingleLine(true)
            setTextColor(ink)
            setHintTextColor(muted)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = panel(Color.rgb(28, 44, 57), 14)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val query = s?.toString().orEmpty()
                    remoteResults = emptyList()
                    renderPhoneResults(query)
                    val generation = ++searchGeneration
                    searchHandler.removeCallbacksAndMessages(null)
                    if (query.trim().length >= 2) searchHandler.postDelayed({
                        YouTubeSearch.search(query) { results ->
                            if (generation == searchGeneration) { remoteResults = results; renderPhoneResults(query) }
                        }
                    }, 450L)
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        body.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        body.addView(space(8))
        queueStatus = text("Queue: ${queueStore.all().size} songs • My karaoke mix: ${playlistStore.all().firstOrNull { it.name == "My karaoke mix" }?.videos?.size ?: 0}", 14f, gold)
        body.addView(queueStatus)
        body.addView(space(8))
        phoneResults = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(phoneResults)
        body.addView(space(24))
        body.addView(text("SING KING • RECENT", 13f, gold, true).apply { letterSpacing = 0.12f })
        body.addView(space(10))
        catalogStatus = text("Checking the channel…", 15f, muted)
        body.addView(catalogStatus)
        preview = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(preview)
        body.addView(space(30))
        body.addView(text("Video appears after you select a song. Some YouTube uploads may block embedding or show ads.", 14f, muted))
        setContentView(scroll)

        SingKingCatalog.initialize(this)
        renderPhoneResults("")
        updateCatalog()
        if (SingKingCatalog.videos.isEmpty() || SingKingCatalog.isStale()) SingKingCatalog.refresh { updateCatalog() }
    }

    private fun requestMorpheCapture() {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_MORPHE_CAPTURE)
    }

    @Deprecated("Activity result API kept small for the development prototype")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_MORPHE_CAPTURE || resultCode != RESULT_OK || data == null) return
        MorpheCaptureGrant.resultCode = resultCode
        MorpheCaptureGrant.data = data
        getSharedPreferences("car_lyrics", MODE_PRIVATE).edit().putBoolean("morphe_mirror_enabled", true).apply()
        if (::morpheStatus.isInitialized) morpheStatus.text = "Enabled. Reopen Car Lyrics on the Mazda, then choose a song."
    }

    private fun renderPhoneResults(query: String) {
        if (!::phoneResults.isInitialized) return
        phoneResults.removeAllViews()
        val normalized = query.trim().lowercase()
        val local = if (normalized.isBlank()) SingKingCatalog.library.take(8) else
            SingKingCatalog.library.filter { it.title.lowercase().contains(normalized) }
        val localIds = local.map { it.id }.toSet()
        val source = (local + remoteResults.filter { it.id !in localIds }).take(if (normalized.isBlank()) 8 else 30)
        if (source.isEmpty()) {
            phoneResults.addView(text(if (normalized.isBlank()) "Search YouTube for any karaoke provider." else "No matching YouTube videos yet.", 14f, muted))
            return
        }
        if (normalized.isNotBlank() && remoteResults.isNotEmpty()) phoneResults.addView(text("YouTube results", 13f, gold, true))
        source.forEach { video ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(8), dp(4), dp(8))
                background = panel(Color.rgb(24, 37, 50), 12)
            }
            row.addView(text(video.title, 14f, ink), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Button(this).apply {
                text = if (queueStore.all().any { it.id == video.id }) "Queued" else "Queue"
                isAllCaps = false
                setOnClickListener { queueStore.toggle(video); updatePhoneQueueStatus(); renderPhoneResults(query) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)))
            row.addView(Button(this).apply {
                text = if (playlistStore.contains("My karaoke mix", video.id)) "In mix" else "Mix"
                isAllCaps = false
                setOnClickListener { playlistStore.toggle("My karaoke mix", video); updatePhoneQueueStatus(); renderPhoneResults(query) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)))
            phoneResults.addView(row)
            phoneResults.addView(space(6))
        }
    }

    private fun updatePhoneQueueStatus() {
        val mix = playlistStore.all().firstOrNull { it.name == "My karaoke mix" }?.videos?.size ?: 0
        queueStatus.text = "Queue: ${queueStore.all().size} songs • My karaoke mix: $mix"
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

    private companion object { const val REQUEST_MORPHE_CAPTURE = 4107 }
}
