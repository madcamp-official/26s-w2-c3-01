package com.example.myapplication.data.remote

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.example.myapplication.BuildConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

class GoogleCredentialClient(context: Context) {
    private val credentialManager = CredentialManager.create(context)

    suspend fun getIdToken(activityContext: Context): String {
        check(isConfigured) { "GOOGLE_WEB_CLIENT_ID is not configured" }
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = credentialManager.getCredential(activityContext, request).credential
        require(
            credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) { "Google ID credential was not returned" }
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }

    companion object {
        val isConfigured: Boolean
            get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()
    }
}
