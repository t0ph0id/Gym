package com.gym.app

import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UIKit.UIViewController
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Google sign-in, implemented in Swift with the GoogleSignIn SDK (iosApp/GoogleSignInBridge.swift).
 * A plain callback keeps the Swift side simple; [IosGoogleAuth] turns it into a suspend function.
 */
interface IosGoogleSignIn {
    /** Calls back once: tokens, or all nulls if the user cancelled, or an [error] message to show. */
    fun signIn(onResult: (idToken: String?, accessToken: String?, error: String?) -> Unit)
    fun signOut()
}

private class IosGoogleAuth(private val native: IosGoogleSignIn) : GoogleAuth {
    override suspend fun signIn(): GoogleTokens? = suspendCancellableCoroutine { cont ->
        native.signIn { idToken, accessToken, error ->
            when {
                error != null -> cont.resumeWithException(UserFacingException(error))
                idToken == null -> cont.resume(null)
                else -> cont.resume(GoogleTokens(idToken, accessToken))
            }
        }
    }

    override suspend fun signOut() = native.signOut()
}

/** The whole app as a UIViewController, for the SwiftUI wrapper in iosApp. */
@Suppress("FunctionName", "unused") // called from Swift
fun MainViewController(firebaseReady: Boolean, googleSignIn: IosGoogleSignIn): UIViewController {
    val platform = IosAppPlatform()
    val googleAuth = IosGoogleAuth(googleSignIn)
    return ComposeUIViewController { App(firebaseReady = firebaseReady, googleAuth = googleAuth, platform = platform) }
}
