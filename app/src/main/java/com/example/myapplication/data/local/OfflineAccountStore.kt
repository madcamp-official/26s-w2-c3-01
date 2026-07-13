package com.example.myapplication.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.myapplication.offlineexchange.ExchangeMusicCard
import com.example.myapplication.offlineexchange.OfflineCredential
import com.google.gson.Gson
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class CachedAccount(
    val accountId: String,
    val displayAlias: String,
    val avatarUrl: String?,
    val colorHex: Long,
    val melodyAlias: String,
    val musicCard: ExchangeMusicCard,
    val lastAuthenticatedAt: Long,
    val offlineCredential: OfflineCredential?,
) {
    val canStartOfflineExchange: Boolean
        get() = offlineCredential != null && offlineCredential.expiresAt > System.currentTimeMillis()
}
class OfflineAccountStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "secure-offline-account",
        Context.MODE_PRIVATE,
    )
    private val alias = "melody-bubble-offline-account-key"
    private val gson = Gson()

    fun save(account: CachedAccount) {
        preferences.edit().putString("account", encrypt(gson.toJson(account))).apply()
    }

    fun load(): CachedAccount? = preferences.getString("account", null)
        ?.let(::decrypt)
        ?.let { runCatching { gson.fromJson(it, CachedAccount::class.java) }.getOrNull() }

    fun clear() = preferences.edit().clear().apply()

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
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
}
