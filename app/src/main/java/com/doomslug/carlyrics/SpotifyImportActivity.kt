package com.doomslug.carlyrics

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.PopupMenu
import android.view.Gravity
import android.graphics.drawable.GradientDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.concurrent.Executors

/** Passenger/parked setup: one entry in Albums, with details kept off the main search screen. */
class SpotifyImportActivity : Activity() {
    private val mint = Color.rgb(137, 220, 198)
    private val ink = Color.rgb(237, 242, 240)
    private val muted = Color.rgb(150, 167, 171)
    private lateinit var body: LinearLayout
    private var reviewing = false
    private val changed: () -> Unit = { if (!isDestroyed) render() }
    override fun onCreate(state: Bundle?) { super.onCreate(state); SpotifyImporter.observe(changed); render() }
    override fun onResume() { super.onResume(); render() }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); render() }
    override fun onDestroy() { SpotifyImporter.unobserve(changed); super.onDestroy() }
    private fun label(value: String, size: Float = 15f, color: Int = ink) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); setPadding(4, 16, 4, 16)
    }
    private fun action(value: String, enabled: Boolean = true, click: () -> Unit) = Button(this).apply {
        text = value; isAllCaps = false; setTextColor(mint); background = null; minHeight = (48 * resources.displayMetrics.density).toInt()
        isEnabled = enabled; setOnClickListener { click() }
    }
    private fun render() {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(16, 23, 27)) }
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        ViewCompat.setOnApplyWindowInsetsListener(body) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(48 + bars.left, 20 + bars.top, 48 + bars.right, 30 + bars.bottom); insets
        }
        scroll.addView(body); setContentView(scroll)
        val lists = SpotifyImportStore(this).all()
        val rows = lists.flatMap { it.songs }
        val heading = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        heading.addView(action("‹  Playlists") { finish() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        heading.addView(action("•••") {
            PopupMenu(this, heading.getChildAt(1)).apply {
                if (!SpotifyImporter.running) {
                    if (SpotifyAccount.connected(this@SpotifyImportActivity)) menu.add("Refresh Spotify playlists").setOnMenuItemClickListener { SpotifyImporter.start(this@SpotifyImportActivity); true }
                    if (lists.isNotEmpty()) menu.add("Recheck unmatched songs").setOnMenuItemClickListener { SpotifyImporter.start(this@SpotifyImportActivity, refresh = false, retryMissing = true); true }
                    menu.add("Import CSV").setOnMenuItemClickListener {
                        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), 4120); true
                    }
                    if (SpotifyAccount.connected(this@SpotifyImportActivity)) menu.add("Disconnect Spotify").setOnMenuItemClickListener { SpotifyAccount.disconnect(this@SpotifyImportActivity); render(); true }
                }
                show()
            }
        }.apply { contentDescription = "Import options" })
        body.addView(heading)
        body.addView(android.widget.ImageView(this).apply {
            setImageResource(R.drawable.spotify_logo); contentDescription = "Spotify"
            scaleType = android.widget.ImageView.ScaleType.FIT_START
        }, LinearLayout.LayoutParams((110 * resources.displayMetrics.density).toInt(), (30 * resources.displayMetrics.density).toInt()).apply { topMargin = 32; bottomMargin = 32 })
        body.addView(label("Karaoke for your playlists", 25f))
        body.addView(label(if (lists.isEmpty()) "Find karaoke versions, then play them here or in the car." else "${lists.size} playlists saved", 14f, muted))
        if (rows.isNotEmpty()) {
            body.addView(label("${rows.count { it.video != null }}", 42f, mint))
            body.addView(label("karaoke matches", 15f, muted))
            val pending = rows.count { it.video == null && it.state != "missing" && it.state != "error" }
            val missing = rows.count { it.video == null && it.state == "missing" }
            val errors = rows.count { it.video == null && it.state == "error" }
            body.addView(label("$pending still to check  ·  $missing no match  ·  $errors to retry", 13f, muted))
        }
        val status = SpotifyImporter.status.ifBlank { if (rows.any { it.video == null && it.state == "pending" }) "Matching is unfinished. Continue to check the remaining songs." else "Saved matches stay in your library." }
        body.addView(label(status, 14f, muted))
        val primary = when {
            SpotifyImporter.running -> action("Pause matching") { SpotifyImporter.stop() }
            !SpotifyAccount.connected(this) && lists.isEmpty() -> action("Connect Spotify") { runCatching { SpotifyAccount.connect(this) }.onFailure { message(it.message ?: "Could not connect") } }
            lists.isEmpty() -> action("Import playlists") { SpotifyImporter.start(this) }
            else -> action("Continue matching") { SpotifyImporter.start(this, refresh = false) }
        }
        primary.background = GradientDrawable().apply { setColor(Color.rgb(29, 40, 45)); cornerRadius = 28 * resources.displayMetrics.density }
        body.addView(primary)
        if (lists.any { it.problem != null }) body.addView(label("${lists.count { it.problem != null }} playlists have partial or restricted source lists. CSV can fill the gaps.", 13f, muted))
        if (!SpotifyImporter.running && lists.isNotEmpty()) body.addView(action(if (reviewing) "Hide song details" else "Review songs & choose versions") { reviewing = !reviewing; render() })
        if (reviewing && !SpotifyImporter.running) lists.forEach { p ->
            body.addView(label(p.name, 18f).apply { setOnClickListener { showPlaylist(p) } })
            body.addView(label(p.summary, 13f, muted).apply { setOnClickListener { showPlaylist(p) } })
        }
    }
    private fun showPlaylist(p: SpotifyPlaylist) {
        val labels = p.songs.map { "${if (it.video != null) "✓" else if (it.state == "missing") "—" else "…"} ${it.song.title} · ${it.song.artists.joinToString()}" }
        AlertDialog.Builder(this).setTitle(p.name).setItems(labels.toTypedArray()) { _, index ->
            val row = p.songs[index]
            AlertDialog.Builder(this).setTitle(row.song.title).setItems(arrayOf("Choose karaoke version", "Open song in Spotify")) { _, action ->
                if (action == 0) startActivity(Intent(this, MainActivity::class.java)
                    .putExtra("search_query", row.song.title + " " + row.song.artists.firstOrNull().orEmpty())
                    .putExtra("match_playlist", p.id).putExtra("match_song", row.song.key))
                else if (row.song.id.matches(Regex("[A-Za-z0-9]{22}")))
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/track/${row.song.id}")))
            }.setNegativeButton("Close", null).show()
        }.setMessage(if (labels.isEmpty()) p.problem ?: "No tracks in this playlist" else null)
            .apply { if (!p.id.startsWith("csv:")) setNeutralButton("Open Spotify") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/playlist/${p.id}")))
            } }.setPositiveButton("Close", null).show()
    }
    private fun message(value: String) { AlertDialog.Builder(this).setMessage(value).setPositiveButton("OK", null).show() }
    @Deprecated("Legacy activity result API")
    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        if (request != 4120 || result != RESULT_OK) return
        val uri = data?.data ?: return
        var name = "Imported playlist"
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) name = it.getString(0).substringBeforeLast('.').take(100)
        }
        Executors.newSingleThreadExecutor().execute {
            val attempt = runCatching {
                val text = contentResolver.openInputStream(uri)!!.bufferedReader().use { reader ->
                    val buffer = CharArray(8192); val out = StringBuilder()
                    while (true) { val n = reader.read(buffer); if (n < 0) break; out.append(buffer, 0, n); require(out.length <= 8_000_000) { "CSV is too large" } }
                    out.toString()
                }
                SpotifyCsv.parse(text, name)
            }
            runOnUiThread { attempt.onSuccess { SpotifyImporter.start(this, imported = it) }.onFailure { message(it.message ?: "Could not read CSV") } }
        }
    }
}
