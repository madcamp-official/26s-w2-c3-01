package com.example.myapplication.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class StoredSession(val accessToken: String, val refreshToken: String?)

class SecureTokenStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("secure-session", Context.MODE_PRIVATE)
    private val alias = "melody-bubble-session-key"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
            generateKey()
        }
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String? = runCatching {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)))
    }.getOrNull()

    fun save(accessToken: String, refreshToken: String?) {
        preferences.edit().putString("access", encrypt(accessToken)).apply {
            if (refreshToken == null) remove("refresh") else putString("refresh", encrypt(refreshToken))
        }.apply()
    }

    fun load(): StoredSession? {
        val access = preferences.getString("access", null)?.let(::decrypt) ?: return null
        val refresh = preferences.getString("refresh", null)?.let(::decrypt)
        return StoredSession(access, refresh)
    }

    fun clear() = preferences.edit().clear().apply()
}
