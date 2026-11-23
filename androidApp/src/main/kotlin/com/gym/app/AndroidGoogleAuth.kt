package com.gym.app

import android.annotation.SuppressLint
import androidx.activity.ComponentActivity
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

class AndroidGoogleAuth(private val activity: ComponentActivity) : GoogleAuth {
    private val credentialManager = CredentialManager.create(activity)

    // default_web_client_id is generated from google-services.json, but only once Google sign-in is
    // enabled in Firebase. Looking it up by name keeps the build working before that.
    @SuppressLint("DiscouragedApi")
    private fun webClientId(): String? {
        val id = activity.resources.getIdentifier("default_web_client_id", "string", activity.packageName)
        return if (id == 0) null else activity.getString(id)
    }

    override suspend fun signIn(): GoogleTokens? {
        val clientId = webClientId() ?: throw UserFacingException(
            "Google sign-in isn't configured. Enable Google in Firebase Authentication, " +
                "then download google-services.json again."
        )
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
            .build()
        return try {
            val credential = credentialManager.getCredential(activity, request).credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                GoogleTokens(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                throw UserFacingException("Google sign-in didn't work. Please try again.")
            }
        } catch (e: GetCredentialCancellationException) {
            null
        } catch (e: NoCredentialException) {
            throw UserFacingException(
                "There's no Google account on this phone. Add one in Settings → Accounts, then try again."
            )
        } catch (e: GetCredentialException) {
            throw UserFacingException("Google sign-in didn't work. Check your connection and try again.")
        }
    }

    override suspend fun signOut() {
        runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
    }
}
