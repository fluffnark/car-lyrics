package com.doomslug.carlyrics

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var url: EditText
    private lateinit var status: TextView
    private val projectionManager by lazy { getSystemService(MediaProjectionManager::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 24)
        }
        fun label(text: String) { column.addView(TextView(this).apply { this.text = text; textSize = 18f }) }
        fun button(text: String, click: () -> Unit) {
            column.addView(Button(this).apply { this.text = text; setOnClickListener { click() } },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        label("Car Lyrics · Morphe mirror")
        label("Park the car. Open a karaoke video in Morphe, then start sharing its app window. Use the Mazda knob to select Show video on the car screen.")
        url = EditText(this).apply { hint = "YouTube karaoke video URL (optional)"; inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI }
        column.addView(url)
        button("Open video in Morphe") { openMorphe() }
        button("Start sharing") {
            startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CAPTURE)
        }
        button("Stop sharing") { startService(Intent(this, CaptureService::class.java).setAction(CaptureService.ACTION_STOP)) }
        status = TextView(this).apply { textSize = 16f }
        column.addView(status)
        setContentView(column)
        refreshStatus()
    }

    override fun onResume() { super.onResume(); refreshStatus() }

    private fun refreshStatus() {
        status.text = if (MirrorState.capturing) "Sharing is active. Select Show video on the car screen."
            else "Sharing is stopped. The phone will ask what to share each time."
    }

    private fun openMorphe() {
        val typed = url.text.toString().trim()
        val intent = if (typed.isEmpty()) packageManager.getLaunchIntentForPackage(MORPHE_PACKAGE)
        else {
            val uri = Uri.parse(typed)
            if (uri.scheme !in listOf("https", "http") || uri.host !in YOUTUBE_HOSTS) {
                status.text = "Enter a youtube.com or youtu.be link."
                return
            }
            Intent(Intent.ACTION_VIEW, uri).setPackage(MORPHE_PACKAGE)
        }
        if (intent == null || intent.resolveActivity(packageManager) == null) {
            status.text = "Morphe YouTube is not installed or cannot open this link."
            return
        }
        startActivity(intent)
    }

    @Deprecated("Activity result API is sufficient for this small prototype")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CAPTURE && resultCode == RESULT_OK && data != null) {
            startForegroundService(Intent(this, CaptureService::class.java)
                .setAction(CaptureService.ACTION_START)
                .putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
                .putExtra(CaptureService.EXTRA_RESULT_DATA, data))
            status.text = "Sharing started. Return to Morphe and select Show video with the Mazda knob."
        }
    }

    companion object {
        private const val REQUEST_CAPTURE = 20
        private const val MORPHE_PACKAGE = "app.morphe.android.youtube"
        private val YOUTUBE_HOSTS = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be")
    }
}
