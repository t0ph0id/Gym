package com.gym.app

import android.app.Activity
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView

/** Shown from Health Connect's permission screens: what Gym reads and why. */
class HealthPrivacyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val text = TextView(this).apply {
            textSize = 16f
            setPadding(pad, pad * 2, pad, pad)
            text = "Gym and Health Connect\n\n" +
                "Gym reads your exercise sessions (for example an elliptical session from your Fitbit), and the " +
                "calories and heart rate during them, only when you set the minutes for a cardio move in a " +
                "workout, so you can pick a session instead of typing.\n\n" +
                "On a finished workout you can also add the calories and heart rate (average and maximum) for " +
                "your whole visit.\n\n" +
                "Only the minutes, calories and heart rate figures you choose are saved in your workout, which " +
                "your training partner can see. Nothing else from Health Connect is stored, uploaded or shared.\n\n" +
                "You can remove Gym's access at any time in Health Connect."
        }
        setContentView(ScrollView(this).apply { addView(text) })
    }
}
