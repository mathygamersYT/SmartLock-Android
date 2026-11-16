package com.example.iot

import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.ClearCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/** Explicit button flow supports existing accounts and first-time registration. */
class GoogleAccountLogin(private val activity: AppCompatActivity) {
    companion object {
        fun signOut(activity: AppCompatActivity, complete: () -> Unit) {
            FirebaseAuth.getInstance().signOut()
            activity.lifecycleScope.launch {
                GoogleAccountLogin(activity).clear()
                if (!activity.isFinishing && !activity.isDestroyed) complete()
            }
        }
    }
    private val manager = CredentialManager.create(activity)
    suspend fun idToken(): String {
        val option = GetSignInWithGoogleOption.Builder(
            activity.getString(R.string.google_server_client_id)).build()
        val result = manager.getCredential(activity, GetCredentialRequest.Builder()
            .addCredentialOption(option).build())
        val credential = result.credential
        check(credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }
    suspend fun clear() {
        try { manager.clearCredentialState(ClearCredentialStateRequest()) }
        catch (_: ClearCredentialException) { /* Firebase session is already signed out. */ }
    }
}
