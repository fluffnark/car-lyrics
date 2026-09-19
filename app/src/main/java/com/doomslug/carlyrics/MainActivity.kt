package com.doomslug.carlyrics

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/** Phone launcher is informational; routine browsing and playback start on the car. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "Car Lyrics\n\nConnect Android Auto and open Car Lyrics on the Mazda screen. Browse recent Sing King Karaoke videos with the Commander knob while parked. No phone setup is needed."
            textSize = 22f
            setPadding(32, 64, 32, 32)
        })
    }
}
