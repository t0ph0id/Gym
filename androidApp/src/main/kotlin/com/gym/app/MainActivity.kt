package com.gym.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.google.firebase.FirebaseApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // FirebaseApp is auto-initialised from google-services.json; if that file was missing at
        // build time there is no app, and we show setup instructions instead of crashing.
        val firebaseReady = FirebaseApp.getApps(this).isNotEmpty()
        val googleAuth = AndroidGoogleAuth(this)
        val platform = AndroidAppPlatform(this)
        setContent { App(firebaseReady = firebaseReady, googleAuth = googleAuth, platform = platform) }
    }

    override fun onStart() {
        super.onStart()
        RestAlarm.onAppShown(this)
    }

    override fun onStop() {
        super.onStop()
        RestAlarm.onAppHidden(this)
    }
}
