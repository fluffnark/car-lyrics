package com.doomslug.carlyrics

import android.app.Activity
import android.content.Intent
import android.content.ActivityNotFoundException
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.inputmethod.EditorInfo
import android.widget.Toast
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
import android.provider.Settings
import rikka.shizuku.Shizuku

/** Session setup and passenger queue; song selection and transport controls also work in the car. */
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
    private lateinit var searchInput: EditText
    private val searchSession by lazy {
        VideoSearchSession(
            localVideos = { SingKingCatalog.library + SingKingCatalog.videos + queueStore.all() },
            changed = { renderPhoneResults() },
        )
    }
    private lateinit var morpheStatus: TextView
    private lateinit var sharingButton: Button
    private lateinit var stopSharingButton: Button
    private lateinit var controlsExplanation: TextView
    private lateinit var nativeButton: Button
    private var awaitingControls = false
    private var requestingCapture = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(nativePermissionResult)
        awaitingControls = savedInstanceState?.getBoolean("awaiting_controls") ?: false
        requestingCapture = savedInstanceState?.getBoolean("requesting_capture") ?: false
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
        body.addView(space(12))
        body.addView(text("CAR LYRICS", 13f, mint, true).apply { letterSpacing = 0.18f })
        body.addView(space(8))
        body.addView(text("Your karaoke stage.", 36f, ink, true))
        body.addView(space(12))
        body.addView(text("Find your song, build a queue, and play with the Mazda Commander knob.", 17f, muted))
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
        val morpheCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = panel(Color.rgb(35, 42, 61), 20)
        }
        morpheCard.addView(text("MORPHE ON YOUR CAR SCREEN", 12f, gold, true).apply { letterSpacing = 0.10f })
        morpheCard.addView(space(8))
        morpheStatus = text("Share only Morphe in Android’s picker. Your existing YouTube login and karaoke video stay in Morphe.", 14f, muted)
        morpheCard.addView(morpheStatus)
        morpheCard.addView(space(10))
        sharingButton = Button(this).apply {
            isAllCaps = false
            setTextColor(Color.rgb(13, 20, 32))
            backgroundTintList = android.content.res.ColorStateList.valueOf(mint)
            setOnClickListener { continueMorpheSetup() }
        }
        morpheCard.addView(sharingButton)
        controlsExplanation = text("Android calls this notification access. Car Lyrics uses it only for Morphe’s playback state and controls; it does not read or save your messages. Return here after enabling access.", 13f, muted)
        morpheCard.addView(controlsExplanation)
        stopSharingButton = Button(this).apply {
            text = "Stop sharing"
            isAllCaps = false
            setOnClickListener { stopService(Intent(this@MainActivity, MorpheProjectionService::class.java)) }
        }
        morpheCard.addView(stopSharingButton)
        nativeButton = Button(this).apply {
            isAllCaps = false
            setOnClickListener {
                if (MorpheNativeDisplay.enabled(this@MainActivity)) {
                    getSharedPreferences("car_lyrics", MODE_PRIVATE).edit().putBoolean("native_morphe", false).apply()
                    updateMorpheStatus()
                } else enableNative()
            }
        }
        morpheCard.addView(nativeButton)
        body.addView(morpheCard)
        body.addView(space(14))
        body.addView(card)
        body.addView(space(30))
        body.addView(text("PASSENGER QUEUE", 13f, mint, true).apply { letterSpacing = 0.14f })
        body.addView(space(8))
        body.addView(text("Add songs from any provider. Native Morphe keeps car video separate from this phone; screen-sharing mode needs Morphe visible.", 15f, muted))
        body.addView(space(10))
        searchInput = EditText(this).apply {
            hint = "Search song, artist, album, or genre"
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setTextColor(ink)
            setHintTextColor(muted)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = panel(Color.rgb(28, 44, 57), 14)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    searchSession.update(s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEARCH) {
                    searchSession.update(text.toString(), submitted = true)
                    true
                } else false
            }
        }
        body.addView(searchInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        body.addView(Button(this).apply {
            text = "Voice search"
            isAllCaps = false
            setOnClickListener { startVoiceSearch() }
        })
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
        body.addView(text("Native mode needs Shizuku running. Screen-sharing backup needs Morphe visible and the phone unlocked; Android may ask for fresh sharing approval.", 14f, muted))
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        layout.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        layout.addView(Button(this).apply {
            text = "Return to current video"
            isAllCaps = false
            setTextColor(Color.rgb(13, 20, 32))
            backgroundTintList = android.content.res.ColorStateList.valueOf(mint)
            setOnClickListener {
                showCurrentVideo()
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
        layout.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(0, 0, 0, insets.getInsets(WindowInsets.Type.systemBars()).bottom)
            insets
        }
        setContentView(layout)

        SingKingCatalog.initialize(this)
        val restoredQuery = savedInstanceState?.getString("search_query").orEmpty()
        searchInput.setText(restoredQuery)
        searchSession.update(restoredQuery)
        updateCatalog()
        if (SingKingCatalog.videos.isEmpty() || SingKingCatalog.isStale()) SingKingCatalog.refresh { updateCatalog() }
    }

    private fun startVoiceSearch() {
        try {
            startActivityForResult(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a song, artist, or karaoke provider")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }, REQUEST_VOICE_SEARCH)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Voice recognition isn't available. You can type your search instead.", Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, "Voice recognition couldn't open. Check your speech app or type your search.", Toast.LENGTH_LONG).show()
        }
    }

    private fun continueMorpheSetup() {
        if (!MorpheMediaAccess.enabled(this)) {
            awaitingControls = true
            runCatching {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, MorpheMediaAccess.component(this).flattenToString()))
            }.onFailure {
                awaitingControls = false
                morpheStatus.text = "Open Android Settings → Notification access → Car Lyrics Morphe controls."
            }
        } else if (MorpheNativeDisplay.enabled(this)) {
            if (!MorpheNativeDisplay.authorized()) enableNative()
            else showCurrentVideo()
        } else if (MorpheCaptureGrant.isGranted) {
            openMorphe()
        } else requestMorpheCapture()
    }

    private val nativePermissionResult = Shizuku.OnRequestPermissionResultListener { code, result ->
        if (code == 4109) {
            if (result == android.content.pm.PackageManager.PERMISSION_GRANTED) activateNative()
            else morpheStatus.text = "Native access wasn’t enabled. Screen sharing is still available."
        }
    }
    private fun showCurrentVideo() {
        val shown = CarPlaybackLink.showPlayer?.invoke() == true
        if (!MorpheNativeDisplay.enabled(this)) openMorphe()
        else Toast.makeText(this, if (shown) "Current video selected on Android Auto"
            else "Open Car Lyrics in Android Auto and select a song first", Toast.LENGTH_LONG).show()
    }
    private fun enableNative() {
        if (!Shizuku.pingBinder()) { morpheStatus.text = "Open Shizuku and tap Start, then return here to enable native Morphe."; return }
        if (MorpheNativeDisplay.authorized()) activateNative()
        else runCatching { Shizuku.requestPermission(4109) }.onFailure {
            morpheStatus.text = "Allow Car Lyrics in Shizuku’s Authorized applications, then try again."
        }
    }
    private fun activateNative() {
        stopService(Intent(this, MorpheProjectionService::class.java))
        getSharedPreferences("car_lyrics", MODE_PRIVATE).edit().putBoolean("native_morphe", true).apply()
        updateMorpheStatus()
    }

    private fun openMorphe(): Boolean {
        val morphe = packageManager.getLaunchIntentForPackage(MorpheMediaAccess.PACKAGE)
        if (morphe == null) { morpheStatus.text = "Install Morphe YouTube on this phone first."; return false }
        return runCatching { startActivity(morphe); true }.getOrElse {
            morpheStatus.text = "Could not open Morphe. Open YouTube Morphe from your phone’s launcher."
            false
        }
    }

    private fun requestMorpheCapture() {
        if (requestingCapture || MorpheCaptureGrant.isGranted) return
        // Put Morphe in the recent-app list before Android asks which app to share.
        // Consent and the choice of app remain with the user.
        if (!openMorphe()) return
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        requestingCapture = true
        runCatching {
            startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_MORPHE_CAPTURE)
        }.onFailure {
            requestingCapture = false
            morpheStatus.text = "Could not open Android’s sharing picker. Try Start Morphe sharing again."
        }
    }

    @Deprecated("Activity result API kept small for the development prototype")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_VOICE_SEARCH) {
            if (resultCode != RESULT_OK) return
            val heard = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull { it.isNotBlank() }?.trim()?.take(200)
            if (heard == null) {
                Toast.makeText(this, "No speech detected. Try again or type your search.", Toast.LENGTH_LONG).show()
            } else {
                searchInput.setText(heard)
                searchInput.setSelection(heard.length)
                searchSession.update(heard, submitted = true)
            }
            return
        }
        if (requestCode != REQUEST_MORPHE_CAPTURE) return
        requestingCapture = false
        if (resultCode != RESULT_OK || data == null) {
            updateMorpheStatus()
            morpheStatus.text = "Sharing wasn’t started. Tap Start Morphe sharing when you’re ready."
            return
        }
        startForegroundService(Intent(this, MorpheProjectionService::class.java).apply {
            action = MorpheProjectionService.ACTION_START
            putExtra(MorpheProjectionService.EXTRA_RESULT_CODE, resultCode)
            putExtra(MorpheProjectionService.EXTRA_DATA, data)
        })
        getSharedPreferences("car_lyrics", MODE_PRIVATE).edit().putBoolean("morphe_mirror_enabled", true).apply()
        updateMorpheStatus()
    }

    private val captureChanged: () -> Unit = { updateMorpheStatus() }

    override fun onStart() {
        super.onStart()
        MorpheCaptureGrant.observe(captureChanged)
        updateMorpheStatus()
    }

    override fun onResume() {
        super.onResume()
        updateMorpheStatus()
        if (awaitingControls) {
            awaitingControls = false
            if (MorpheMediaAccess.enabled(this) && !MorpheCaptureGrant.isGranted && !MorpheNativeDisplay.enabled(this)) requestMorpheCapture()
        }
    }

    override fun onStop() {
        MorpheCaptureGrant.removeObserver(captureChanged)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("search_query", searchInput.text.toString())
        outState.putBoolean("awaiting_controls", awaitingControls)
        outState.putBoolean("requesting_capture", requestingCapture)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(nativePermissionResult)
        searchSession.close()
        super.onDestroy()
    }

    private fun updateMorpheStatus() {
        if (!::morpheStatus.isInitialized) return
        val controlsReady = MorpheMediaAccess.enabled(this)
        val sharing = MorpheCaptureGrant.isGranted
        val native = MorpheNativeDisplay.enabled(this)
        nativeButton.text = if (native) "Use screen-sharing backup" else "Enable native Morphe • Shizuku"
        sharingButton.text = when {
            !controlsReady -> "Enable Morphe controls"
            native -> if (MorpheNativeDisplay.authorized()) "Show current video on car" else "Reconnect native Morphe"
            sharing -> "Return to Morphe"
            else -> "Start Morphe sharing"
        }
        controlsExplanation.visibility = if (controlsReady) View.GONE else View.VISIBLE
        stopSharingButton.visibility = if (sharing) View.VISIBLE else View.GONE
        morpheStatus.text = when {
            !controlsReady -> "One-time setup: enable playback controls, then share Morphe."
            native -> "Native Morphe is enabled. Select a song in Android Auto; no sharing picker is needed while Shizuku is running. Your phone stays free for the queue."
            sharing -> "Sharing is ready. Use the car to search, choose a song, and skip. Return to Morphe after using the passenger queue."
            else -> "Choose Share one app → Next → YouTube Morphe. Then select a song on the car screen."
        }
    }

    private fun renderPhoneResults() {
        if (!::phoneResults.isInitialized) return
        phoneResults.removeAllViews()
        val source = searchSession.videos.take(if (searchSession.query.isBlank()) 8 else 30)
        if (searchSession.loading) phoneResults.addView(text("Searching YouTube…", 14f, gold))
        searchSession.error?.let { error ->
            phoneResults.addView(text(error, 14f, gold))
            phoneResults.addView(Button(this).apply {
                text = "Retry search"
                isAllCaps = false
                setOnClickListener { searchSession.update(searchSession.query, submitted = true, retry = true) }
            })
        }
        if (source.isEmpty()) {
            if (!searchSession.loading && searchSession.error == null) phoneResults.addView(text(
                if (searchSession.query.isBlank()) "Search YouTube for any karaoke provider."
                else "No results. Try the song and artist, or another provider.", 14f, muted))
            return
        }
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
                setOnClickListener { queueStore.toggle(video); updatePhoneQueueStatus(); renderPhoneResults() }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)))
            row.addView(Button(this).apply {
                text = if (playlistStore.contains("My karaoke mix", video.id)) "In mix" else "Mix"
                isAllCaps = false
                setOnClickListener { playlistStore.toggle("My karaoke mix", video); updatePhoneQueueStatus(); renderPhoneResults() }
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

    private companion object {
        const val REQUEST_MORPHE_CAPTURE = 4107
        const val REQUEST_VOICE_SEARCH = 4108
    }
}
