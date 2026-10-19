package com.doomslug.carlyrics

import android.app.AlertDialog
import android.widget.CheckBox
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.net.Uri
import android.app.Activity
import android.content.Intent
import android.content.ActivityNotFoundException
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.widget.ImageButton
import android.widget.PopupMenu
import android.text.TextUtils
import android.view.Gravity
import android.content.res.ColorStateList
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
    private val ink = Color.rgb(237, 242, 240)
    private val muted = Color.rgb(150, 167, 171)
    private val mint = Color.rgb(137, 220, 198)
    private val gold = Color.rgb(245, 206, 130)
    private lateinit var phoneResults: LinearLayout
    private lateinit var phoneScroll: ScrollView
    private lateinit var queuePanel: LinearLayout
    private lateinit var queueToggle: Button
    private enum class Page { SEARCH, QUEUE, ALBUMS }
    private var page = Page.SEARCH
    private lateinit var exploreTab: Button
    private lateinit var albumsTab: Button
    private lateinit var albumsPanel: LinearLayout
    private lateinit var connectionLabel: TextView
    private lateinit var nowPlayingBar: LinearLayout
    private lateinit var nowPlayingArt: ImageView
    private lateinit var nowPlayingTitle: TextView
    private lateinit var rootLayout: LinearLayout
    private var connectionDialog: AlertDialog? = null
    private val queueBadges = mutableMapOf<String, TextView>()
    private val queuePreferences by lazy { getSharedPreferences("karaoke_queue", MODE_PRIVATE) }
    private val queueChanged = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "items") runOnUiThread { refreshPhoneQueue() }
    }
    private val queueStore by lazy { QueueStore(this) }
    private val playlistStore by lazy { PlaylistStore(this) }
    private lateinit var searchInput: EditText
    private val searchSession by lazy {
        VideoSearchSession(
            localVideos = { SingKingCatalog.videos + SingKingCatalog.library + queueStore.all() +
                playlistStore.all().flatMap { it.videos } + SavedVideos(this).all() },
            changed = { renderPhoneResults() },
        )
    }
    private lateinit var morpheStatus: TextView
    private lateinit var sharingButton: Button
    private val thumbnailViews = mutableMapOf<String, ImageView>()
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val binderReceived = Shizuku.OnBinderReceivedListener { runOnUiThread { updateMorpheStatus() } }
    private val binderDead = Shizuku.OnBinderDeadListener { runOnUiThread { updateMorpheStatus() } }
    private val refreshConnection = object : Runnable {
        override fun run() {
            if (!isDestroyed) {
                if (MorpheNativeDisplay.enabled(this@MainActivity) && !MorpheNativeDisplay.running())
                    ShizukuConnection.refresh(this@MainActivity)
                updateNowPlaying()
                mainHandler.postDelayed(this, 2_500)
            }
        }
    }
    private var awaitingNative = false
    private var awaitingControls = false
    private var requestingCapture = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(nativePermissionResult)
        Shizuku.addBinderReceivedListener(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        awaitingControls = savedInstanceState?.getBoolean("awaiting_controls") ?: false
        awaitingNative = savedInstanceState?.getBoolean("awaiting_native") ?: false
        requestingCapture = savedInstanceState?.getBoolean("requesting_capture") ?: false
        page = Page.entries.getOrElse(savedInstanceState?.getInt("phone_page", 0) ?: 0) { Page.SEARCH }
        morpheStatus = text("", 15f, muted)
        sharingButton = quietButton("") {}

        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(16, 23, 27))
            isFocusableInTouchMode = true
        }
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(dp(20) + bars.left, dp(12) + bars.top, dp(20) + bars.right,
                maxOf(bars.bottom, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom))
            insets
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(text("Car Lyrics", 27f, ink, true), LinearLayout.LayoutParams(0, dp(52), 1f))
        connectionLabel = text("", 12f, mint).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(4), 0)
            contentDescription = "Car connection"
            background = ripple(24)
            setOnClickListener { showConnection() }
        }
        header.addView(connectionLabel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)))
        rootLayout.addView(header)
        rootLayout.addView(space(16))

        val searchBar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(4), 0)
            background = panel(Color.rgb(29, 40, 45), 28)
        }
        searchInput = EditText(this).apply {
            hint = "Search songs or artists"
            contentDescription = "Search songs or artists"
            setSingleLine(true)
            textSize = 16f
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setTextColor(ink); setHintTextColor(muted)
            setPadding(0, 0, dp(4), 0)
            background = null
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (s?.isNotBlank() == true && page != Page.SEARCH) showPage(Page.SEARCH)
                    searchSession.update(s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEARCH) {
                    searchSession.update(text.toString(), submitted = true)
                    hideKeyboard()
                    true
                } else false
            }
        }
        searchBar.addView(searchInput, LinearLayout.LayoutParams(0, dp(54), 1f))
        searchBar.addView(iconButton(R.drawable.ic_mic, "Voice search") { startVoiceSearch() })
        searchBar.addView(iconButton(R.drawable.ic_filter, "Search filters") { showFilters() })
        rootLayout.addView(searchBar)
        rootLayout.addView(space(12))
        val tabs = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        exploreTab = quietButton("Explore") { showPage(Page.SEARCH) }
        queueToggle = quietButton("Queue") { showPage(Page.QUEUE) }
        albumsTab = quietButton("Albums") { showPage(Page.ALBUMS) }
        listOf(exploreTab, queueToggle, albumsTab).forEach {
            tabs.addView(it, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        rootLayout.addView(tabs)
        rootLayout.addView(divider())
        phoneScroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(20))
        }
        queuePanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        albumsPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        phoneResults = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(phoneResults); content.addView(queuePanel); content.addView(albumsPanel)
        phoneScroll.addView(content)
        rootLayout.addView(phoneScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        nowPlayingBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(8))
            background = ripple(12)
            setOnClickListener { hideKeyboard(); showCurrentVideo() }
            contentDescription = "Return to current video"
        }
        nowPlayingArt = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = panel(Color.rgb(29, 40, 45), 6)
            clipToOutline = true
        }
        nowPlayingBar.addView(nowPlayingArt, LinearLayout.LayoutParams(dp(48), dp(30)).apply { marginEnd = dp(12) })
        val nowText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        nowText.addView(text("On car", 11f, mint))
        nowPlayingTitle = text("", 14f, ink).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        nowText.addView(nowPlayingTitle)
        nowPlayingBar.addView(nowText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        nowPlayingBar.addView(text("↗", 22f, mint), LinearLayout.LayoutParams(dp(32), dp(40)))
        rootLayout.addView(nowPlayingBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        setContentView(rootLayout)
        rootLayout.requestFocus()
        SingKingCatalog.initialize(this)
        val options = VideoSearchOptions(savedInstanceState?.getBoolean("karaoke_only", true) ?: true,
            savedInstanceState?.getString("preferred_provider").orEmpty(),
            savedInstanceState?.getBoolean("library_only", false) ?: false)
        val restored = intent.getStringExtra("search_query") ?: savedInstanceState?.getString("search_query").orEmpty()
        val restoredPage = page
        searchInput.setText(restored)
        searchSession.update(restored, options = options)
        showPage(restoredPage)
        updateMorpheStatus()
        updateNowPlaying()
        if (SingKingCatalog.videos.isEmpty() || SingKingCatalog.isStale()) SingKingCatalog.refresh { updateCatalog() }
    }

    private fun hideKeyboard() {
        searchInput.clearFocus()
        rootLayout.requestFocus()
        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.ime())
    }

    private fun showPage(next: Page) {
        page = next
        if (!::phoneResults.isInitialized) return
        phoneResults.visibility = if (page == Page.SEARCH) View.VISIBLE else View.GONE
        queuePanel.visibility = if (page == Page.QUEUE) View.VISIBLE else View.GONE
        albumsPanel.visibility = if (page == Page.ALBUMS) View.VISIBLE else View.GONE
        listOf(exploreTab to Page.SEARCH, queueToggle to Page.QUEUE, albumsTab to Page.ALBUMS).forEach { (tab, target) ->
            tab.setTextColor(if (page == target) mint else muted)
            tab.typeface = Typeface.create("sans-serif", if (page == target) Typeface.BOLD else Typeface.NORMAL)
        }
        updatePhoneQueueStatus()
        if (page == Page.QUEUE) renderQueue()
        if (page == Page.ALBUMS) renderAlbums()
        phoneScroll.smoothScrollTo(0, 0)
    }

    private fun showFilters() {
        val options = searchSession.options
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), 0) }
        fun check(label: String, value: Boolean) = CheckBox(this).apply {
            text = label; setTextColor(ink); isChecked = value
            buttonTintList = ColorStateList.valueOf(mint)
        }
        val karaoke = check("Prefer karaoke versions", options.karaokeOnly)
        val offline = check("Library only", options.libraryOnly)
        val providers = listOf("All providers", "Sing King", "KaraFun", "Sing2Piano", "Zoom Karaoke")
        val provider = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, providers)
            setSelection(providers.indexOf(options.provider).coerceAtLeast(0))
        }
        body.addView(karaoke)
        body.addView(text("Preferred provider", 13f, muted).apply { setPadding(dp(4), dp(16), 0, dp(4)) })
        body.addView(provider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        body.addView(offline)
        showDialog(AlertDialog.Builder(this).setTitle("Search filters").setView(body)
            .setNegativeButton("Cancel", null).setPositiveButton("Apply") { _, _ ->
                showPage(Page.SEARCH)
                searchSession.update(searchInput.text.toString(), submitted = true, options = VideoSearchOptions(
                    karaoke.isChecked, if (provider.selectedItemPosition == 0) "" else provider.selectedItem.toString(), offline.isChecked))
            })
    }

    private fun showConnection() {
        hideKeyboard()
        (morpheStatus.parent as? ViewGroup)?.removeView(morpheStatus)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(4), dp(24), 0) }
        body.addView(morpheStatus)
        sharingButton = quietButton("") { connectOrRepair() }.apply {
            background = panel(mint, 24); setTextColor(Color.rgb(16, 23, 27))
        }
        body.addView(sharingButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(20) })
        body.addView(quietButton("More options") { button -> showConnectionOptions(button) })
        connectionDialog = showDialog(AlertDialog.Builder(this).setTitle("Car connection").setView(body).setNegativeButton("Done", null))
        updateMorpheStatus()
    }

    private fun connectOrRepair() {
        when {
            !MorpheMediaAccess.enabled(this) -> { awaitingNative = true; continueMorpheSetup() }
            !MorpheNativeDisplay.enabled(this) || !MorpheNativeDisplay.authorized() -> enableNative()
            else -> Toast.makeText(this, if (CarPlaybackLink.repair?.invoke() == true) "Restoring your video…"
                else "Ready. Open Car Lyrics in Android Auto.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showConnectionOptions(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add("Open Shizuku").setOnMenuItemClickListener { openShizuku(); true }
            menu.add("Open Morphe").setOnMenuItemClickListener { openMorphe(); true }
            menu.add("Use screen sharing").setOnMenuItemClickListener {
                getSharedPreferences("car_lyrics", MODE_PRIVATE).edit().putBoolean("native_morphe", false).apply()
                continueMorpheSetup(); updateMorpheStatus(); true
            }
            if (MorpheCaptureGrant.isGranted) menu.add("Stop sharing").setOnMenuItemClickListener {
                stopService(Intent(this@MainActivity, MorpheProjectionService::class.java)); true
            }
            menu.add("USB recovery help").setOnMenuItemClickListener {
                showDialog(AlertDialog.Builder(this@MainActivity).setTitle("USB recovery")
                    .setMessage("With this phone connected, run tools/caraoke repair on your computer. After a reboot, you can also open Shizuku and tap Start under Wireless debugging.")
                    .setPositiveButton("Done", null)); true
            }
            show()
        }
    }

    private fun updateNowPlaying() {
        if (!::nowPlayingBar.isInitialized) return
        val video = CarPlaybackLink.currentVideo?.invoke()
        nowPlayingBar.visibility = if (video == null) View.GONE else View.VISIBLE
        val title = video?.let { displayTitle(it.title) }.orEmpty()
        if (nowPlayingTitle.text.toString() != title) nowPlayingTitle.text = title
        val bitmap = video?.let { Thumbnails.get(it.id) }
        if (bitmap != null && nowPlayingArt.tag !== bitmap) {
            nowPlayingArt.setImageBitmap(bitmap)
            nowPlayingArt.tag = bitmap
        } else if (bitmap == null && nowPlayingArt.tag != video?.id) {
            nowPlayingArt.setImageDrawable(null)
            nowPlayingArt.tag = video?.id
        }
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
    private fun openShizuku() {
        val intent = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
        if (intent == null) {
            morpheStatus.text = "Install Shizuku, start it, then return here. USB recovery: tools/caraoke repair."
            return
        }
        awaitingNative = true
        runCatching { startActivity(intent) }.onFailure {
            morpheStatus.text = "Open Shizuku from your launcher. Tap Start under Wireless debugging, then return here."
        }
    }

    private fun enableNative() {
        if (!MorpheNativeDisplay.running()) { openShizuku(); return }
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
        mainHandler.post(refreshConnection)
        MorpheCaptureGrant.observe(captureChanged)
        updateMorpheStatus()
        queuePreferences.registerOnSharedPreferenceChangeListener(queueChanged)
        refreshPhoneQueue()
    }

    override fun onResume() {
        super.onResume()
        if (::albumsPanel.isInitialized && page == Page.ALBUMS) renderAlbums()
        updateMorpheStatus()
        if (awaitingNative && MorpheNativeDisplay.running()) {
            awaitingNative = false
            enableNative()
        }
        if (awaitingControls) {
            awaitingControls = false
            if (MorpheMediaAccess.enabled(this)) {
                if (awaitingNative) enableNative()
                else if (!MorpheCaptureGrant.isGranted && !MorpheNativeDisplay.enabled(this)) requestMorpheCapture()
            }
        }
    }

    override fun onStop() {
        mainHandler.removeCallbacks(refreshConnection)
        queuePreferences.unregisterOnSharedPreferenceChangeListener(queueChanged)
        MorpheCaptureGrant.removeObserver(captureChanged)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("karaoke_only", searchSession.options.karaokeOnly)
        outState.putBoolean("library_only", searchSession.options.libraryOnly)
        outState.putString("preferred_provider", searchSession.options.provider)
        outState.putInt("phone_page", page.ordinal)
        outState.putBoolean("awaiting_native", awaitingNative)
        outState.putString("search_query", searchInput.text.toString())
        outState.putBoolean("awaiting_controls", awaitingControls)
        outState.putBoolean("requesting_capture", requestingCapture)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(nativePermissionResult)
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        mainHandler.removeCallbacksAndMessages(null)
        searchSession.close()
        connectionDialog?.dismiss()
        super.onDestroy()
    }

    private fun updateMorpheStatus() {
        if (!::connectionLabel.isInitialized) return
        if (MorpheNativeDisplay.enabled(this)) ShizukuConnection.refresh(this)
        val controls = MorpheMediaAccess.enabled(this)
        val native = MorpheNativeDisplay.enabled(this)
        val running = MorpheNativeDisplay.running()
        val authorized = MorpheNativeDisplay.authorized()
        val installed = packageManager.getLaunchIntentForPackage(MorpheMediaAccess.PACKAGE) != null
        val ready = installed && controls && ((native && authorized) || (!native && MorpheCaptureGrant.isGranted))
        connectionLabel.text = if (ready) "Ready  ⌄" else "Setup  ⌄"
        connectionLabel.setTextColor(if (ready) mint else gold)
        morpheStatus.text = when {
            !installed -> "Install Morphe YouTube on this phone to play videos."
            !controls -> "Enable playback controls in Android Settings. Android calls this notification access; Car Lyrics uses Morphe’s playback state and controls and does not save your messages."
            native && !running -> "Open Shizuku and tap Start, then return here. You may need to do this again after a phone reboot."
            native && !authorized -> "Allow this Car Lyrics app to connect to Shizuku when prompted."
            native -> "Ready for Android Auto. Your phone stays free for search."
            MorpheCaptureGrant.isGranted -> "Screen sharing is active. Keep Morphe visible and your phone unlocked."
            else -> "Connect with Shizuku to play on your car and keep your phone free."
        }
        sharingButton.text = when {
            !controls -> "Enable controls"
            native && !running -> "Open Shizuku"
            !native -> "Connect to car"
            !authorized -> "Allow connection"
            else -> "Repair picture"
        }
    }

    private fun renderPhoneResults() {
        if (!::phoneResults.isInitialized) return
        queueBadges.clear()
        if (page == Page.SEARCH) thumbnailViews.clear()
        phoneResults.removeAllViews()
        val source = searchSession.videos.take(if (searchSession.query.isBlank()) 15 else 30)
        if (searchSession.loading) phoneResults.addView(text("Searching…", 13f, muted).apply { setPadding(dp(4), dp(12), 0, dp(8)) })
        searchSession.error?.let {
            phoneResults.addView(text("Online search unavailable. Showing saved matches.", 13f, muted).apply { setPadding(dp(4), dp(12), 0, dp(4)) })
            phoneResults.addView(quietButton("Retry") { searchSession.update(searchSession.query, submitted = true, retry = true) })
        }
        if (source.isEmpty() && !searchSession.loading) phoneResults.addView(text(
            if (searchSession.query.isBlank()) "Find a song to get started." else "No songs found. Try another search.", 15f, muted).apply { setPadding(dp(4), dp(28), 0, 0) })
        source.forEach { video -> phoneResults.addView(songRow(video)) }
        Thumbnails.request(source) { if (!isDestroyed) thumbnailViews.forEach { (id, view) -> Thumbnails.get(id)?.let(view::setImageBitmap) } }
    }

    private fun displayTitle(title: String) = title
        .replace(Regex("\\s*\\(Karaoke(?: Version)?\\)\\s*$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s*\\|\\s*Karaoke Version\\s*\\|", RegexOption.IGNORE_CASE), " ·")
        .trim()

    private fun songRow(video: KaraokeVideo, queueIndex: Int? = null): View {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
            background = ripple(12)
            setOnClickListener { playNow(video) }
            contentDescription = "Play now: ${video.title}"
        }
        row.addView(ImageView(this).apply {
            contentDescription = null
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = panel(Color.rgb(29, 40, 45), 8)
            clipToOutline = true
            setImageBitmap(Thumbnails.get(video.id))
            if ((page == Page.SEARCH && queueIndex == null) || (page == Page.QUEUE && queueIndex != null))
                thumbnailViews[video.id] = this
        }, LinearLayout.LayoutParams(dp(88), dp(52)).apply { marginEnd = dp(12) })
        val title = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        title.addView(text(displayTitle(video.title), 15f, ink).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            maxLines = 2; ellipsize = TextUtils.TruncateAt.END
        })
        if (queueIndex == null) {
            title.addView(text("In queue", 11f, mint).apply {
                visibility = if (queueStore.all().any { it.id == video.id }) View.VISIBLE else View.GONE
                queueBadges[video.id] = this
            })
        }
        row.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(iconButton(R.drawable.ic_more, "More actions: ${video.title}") { anchor -> songActions(anchor, video, queueIndex) })
        return row
    }

    private fun songActions(anchor: View, video: KaraokeVideo, queueIndex: Int? = null) {
        hideKeyboard()
        PopupMenu(this, anchor).apply {
            val importedPlaylist = intent.getStringExtra("match_playlist")
            val importedSong = intent.getStringExtra("match_song")
            if (importedPlaylist != null && importedSong != null) menu.add("Use for this Spotify song").setOnMenuItemClickListener {
                val saved = SpotifyImportStore(this@MainActivity).choose(importedPlaylist, importedSong, video)
                Toast.makeText(this@MainActivity, if (saved) "Karaoke version saved" else "Playlist no longer available", Toast.LENGTH_SHORT).show()
                if (saved) finish()
                true
            }
            menu.add("Play now").setOnMenuItemClickListener { playNow(video); true }
            menu.add("Play next").setOnMenuItemClickListener {
                if (CarPlaybackLink.playNext?.invoke(video) != true) queueStore.playNext(video, null)
                refreshPhoneQueue(); Toast.makeText(this@MainActivity, "Added next", Toast.LENGTH_SHORT).show(); true
            }
            val queued = queueStore.all().any { it.id == video.id }
            menu.add(if (queued) "Queued" else "Queue").apply {
                isEnabled = !queued
                setOnMenuItemClickListener { queueStore.add(video); refreshPhoneQueue(); true }
            }
            menu.add("Add to album").setOnMenuItemClickListener { chooseAlbum(video); true }
            if (queueIndex != null) {
                if (queueIndex > 0) menu.add("Move up").setOnMenuItemClickListener { queueStore.move(video.id, -1); true }
                if (queueIndex < queueStore.all().lastIndex) menu.add("Move down").setOnMenuItemClickListener { queueStore.move(video.id, 1); true }
                menu.add("Remove from queue").setOnMenuItemClickListener { queueStore.remove(video.id); true }
            }
            show()
        }
    }

    private fun playNow(video: KaraokeVideo) {
        hideKeyboard()
        queueStore.add(video)
        if (CarPlaybackLink.playVideo?.invoke(video) == true) return
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=${video.id}"))
                .setPackage(MorpheMediaAccess.PACKAGE))
        }.onFailure { Toast.makeText(this, "Could not open Morphe. Check setup above.", Toast.LENGTH_LONG).show() }
    }

    private fun chooseAlbum(video: KaraokeVideo) {
        val names = playlistStore.all().filter { it.spotifyId == null }.map { it.name }
        fun add(name: String) {
            val added = playlistStore.add(name, video)
            updatePhoneQueueStatus()
            Toast.makeText(this, if (added) "Added to $name" else "Already in $name", Toast.LENGTH_SHORT).show()
        }
        showDialog(AlertDialog.Builder(this).setTitle("Add to album")
            .setItems((names + "New album…").toTypedArray()) { _, index ->
                if (index < names.size) add(names[index])
                else {
                    val input = EditText(this).apply {
                        hint = "Album name"; setSingleLine(true); setTextColor(ink)
                        backgroundTintList = ColorStateList.valueOf(mint)
                    }
                    val body = LinearLayout(this).apply { setPadding(dp(24), 0, dp(24), 0) }
                    body.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
                    val dialog = showDialog(AlertDialog.Builder(this).setTitle("New album").setView(body)
                        .setNegativeButton("Cancel", null).setPositiveButton("Add", null))
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val name = input.text.toString().trim().take(80)
                        if (name.isBlank()) input.error = "Enter a name"
                        else { add(name); dialog.dismiss() }
                    }
                }
            }.setNegativeButton("Cancel", null))
    }

    private fun refreshPhoneQueue() {
        if (!::queuePanel.isInitialized) return
        updatePhoneQueueStatus()
        val ids = queueStore.all().map { it.id }.toSet()
        queueBadges.forEach { (id, label) -> label.visibility = if (id in ids) View.VISIBLE else View.GONE }
        if (page == Page.QUEUE) renderQueue()
        updateNowPlaying()
    }

    private fun renderQueue() {
        if (!::queuePanel.isInitialized || page != Page.QUEUE) return
        val previousScroll = phoneScroll.scrollY
        queuePanel.removeAllViews()
        thumbnailViews.clear()
        val videos = queueStore.all()
        if (videos.isEmpty()) queuePanel.addView(text("Your queue is empty. Add songs from ⋮.", 15f, muted).apply { setPadding(dp(4), dp(28), 0, 0) })
        videos.forEachIndexed { index, video -> queuePanel.addView(songRow(video, index)) }
        Thumbnails.request(videos) { if (!isDestroyed) thumbnailViews.forEach { (id, view) -> Thumbnails.get(id)?.let(view::setImageBitmap) } }
        phoneScroll.post { phoneScroll.scrollTo(0, previousScroll) }
    }

    private fun renderAlbums() {
        albumsPanel.removeAllViews()
        albumsPanel.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; background = ripple(12); contentDescription = "Spotify playlists"
            setPadding(dp(4), dp(16), dp(4), dp(16))
            setOnClickListener { startActivity(Intent(this@MainActivity, SpotifyImportActivity::class.java)) }
            addView(ImageView(this@MainActivity).apply { setImageResource(R.drawable.spotify_logo); scaleType = ImageView.ScaleType.FIT_START },
                LinearLayout.LayoutParams(dp(110), dp(30)).apply { marginEnd = dp(18) })
            addView(text("Playlists  ›", 15f, muted))
        })
        val albums = playlistStore.all().filter { it.videos.isNotEmpty() || it.spotifyId != null }
        if (albums.isEmpty()) albumsPanel.addView(text("Save songs to an album from ⋮.", 15f, muted).apply { setPadding(dp(4), dp(28), 0, 0) })
        albums.forEach { album ->
            albumsPanel.addView(LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4), dp(18), dp(4), dp(18))
                background = ripple(12)
                setOnClickListener { openAlbum(album) }
                addView(ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.ic_saved); imageTintList = ColorStateList.valueOf(mint)
                }, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(18) })
                val title = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
                title.addView(text(album.name.substringBefore(" · Spotify ["), 16f, ink, true))
                title.addView(text(album.detail ?: if (album.videos.size == 1) "1 song" else "${album.videos.size} songs", 12f, muted))
                addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(text("›", 24f, muted))
            })
        }
    }

    private fun openAlbum(album: KaraokePlaylist) {
        if (album.videos.isEmpty() && album.spotifyId != null) {
            startActivity(Intent(this, SpotifyImportActivity::class.java)); return
        }
        showDialog(AlertDialog.Builder(this).setTitle(album.name.substringBefore(" · Spotify ["))
            .setItems(album.videos.map { it.title }.toTypedArray()) { _, index ->
                val video = album.videos[index]
                showDialog(AlertDialog.Builder(this).setTitle(displayTitle(video.title))
                    .setItems(arrayOf("Play now", "Play next", "Queue", if (album.spotifyId == null) "Remove from album" else "Add to album")) { _, action ->
                        when (action) {
                            0 -> playNow(video)
                            1 -> { if (CarPlaybackLink.playNext?.invoke(video) != true) queueStore.playNext(video, null); refreshPhoneQueue() }
                            2 -> { queueStore.add(video); refreshPhoneQueue() }
                            3 -> { if (album.spotifyId != null) chooseAlbum(video)
                                else { if (playlistStore.contains(album.name, video.id)) playlistStore.toggle(album.name, video); renderAlbums() } }
                        }
                    }.setNegativeButton("Cancel", null))
            }.setNegativeButton("Close", null))
    }

    private fun updatePhoneQueueStatus() {
        if (!::queueToggle.isInitialized) return
        queueToggle.text = "Queue  ${queueStore.all().size}"
    }

    private fun updateCatalog() {
        if (!isDestroyed && searchSession.query.isBlank()) searchSession.update("", retry = true)
    }

    private fun iconButton(resource: Int, label: String, action: (View) -> Unit) = ImageButton(this).apply {
        contentDescription = label
        setImageResource(resource)
        imageTintList = ColorStateList.valueOf(muted)
        background = ripple(24)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(48))
        setOnClickListener { action(it) }
    }

    private fun quietButton(label: String, action: (View) -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 14f
        setTextColor(muted); setPadding(dp(12), 0, dp(12), 0)
        minWidth = 0; minimumWidth = 0; minHeight = dp(48); minimumHeight = dp(48)
        background = ripple(24)
        stateListAnimator = null
        setOnClickListener { action(it) }
    }

    private fun showDialog(builder: AlertDialog.Builder): AlertDialog = builder.create().apply {
        setOnShowListener {
            window?.setBackgroundDrawable(panel(Color.rgb(28, 38, 43), 24))
            listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL).forEach { getButton(it)?.setTextColor(mint) }
        }
        show()
    }

    private fun ripple(radius: Int) = RippleDrawable(ColorStateList.valueOf(Color.argb(35, 137, 220, 198)), null, panel(Color.WHITE, radius))
    private fun divider() = View(this).apply {
        setBackgroundColor(Color.rgb(38, 49, 54))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
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
