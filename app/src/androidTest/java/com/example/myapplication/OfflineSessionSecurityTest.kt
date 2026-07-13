package com.example.myapplication

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.util.Base64
import com.example.myapplication.data.local.CachedAccount
import com.example.myapplication.data.local.OfflineAccountStore
import com.example.myapplication.offlineexchange.ExchangeCrypto
import com.example.myapplication.offlineexchange.ExchangeMusicCard
import com.example.myapplication.offlineexchange.ExchangeProtocol
import com.example.myapplication.offlineexchange.OfflineCredential
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineSessionSecurityTest {
    @Test
    fun serverCredentialSignatureVerifiesOnAndroid() {
        val unsigned = OfflineCredential(
            credentialId = "11111111-1111-4111-8111-111111111111",
            publicSubject = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
            devicePublicKey = "device-public-key",
            displayAlias = "Listener",
            issuedAt = 1L,
            expiresAt = Long.MAX_VALUE,
            serverSignature = "",
            serverPublicKey = Base64.encodeToString(hex(SERVER_PUBLIC_KEY_X509), Base64.NO_WRAP),
        )
        val message = ExchangeProtocol.credentialCanonical(unsigned).toByteArray(Charsets.UTF_8)
        val signature = Ed25519Signer().run {
            init(true, Ed25519PrivateKeyParameters(hex(SERVER_PRIVATE_KEY_SEED), 0))
            update(message, 0, message.size)
            Base64.encodeToString(generateSignature(), Base64.NO_WRAP)
        }
        val credential = unsigned.copy(serverSignature = signature)
        val crypto = ExchangeCrypto()

        assertTrue(crypto.verifyServer(credential, credential.serverPublicKey))
        assertFalse(crypto.verifyServer(credential.copy(displayAlias = "Tampered"), credential.serverPublicKey))
    }

    @Test
    fun logoutArtifactsRemoveCachedAccountAndRotateDeviceKey() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val accountStore = OfflineAccountStore(context)
        val crypto = ExchangeCrypto()
        val firstPublicKey = crypto.publicKeyBase64()
        accountStore.save(
            CachedAccount(
                accountId = "account-a",
                displayAlias = "Listener",
                avatarUrl = null,
                colorHex = 0x6750A4,
                melodyAlias = "C6",
                musicCard = ExchangeMusicCard("Listener", "Song", "Artist", "C6"),
                lastAuthenticatedAt = 1L,
                offlineCredential = OfflineCredential(
                    credentialId = "credential",
                    publicSubject = "subject",
                    devicePublicKey = firstPublicKey,
                    displayAlias = "Listener",
                    issuedAt = 1L,
                    expiresAt = Long.MAX_VALUE,
                    serverSignature = "signature",
                    serverPublicKey = "server-key",
                ),
            )
        )
        assertNotNull(accountStore.load())

        accountStore.clear()
        crypto.deleteDeviceKey()

        assertNull(accountStore.load())
        assertNotEquals(firstPublicKey, crypto.publicKeyBase64())
        crypto.deleteDeviceKey()
    }

    private fun hex(value: String): ByteArray = value.chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()

    private companion object {
        const val SERVER_PRIVATE_KEY_SEED =
            "9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60"
        const val SERVER_PUBLIC_KEY_X509 =
            "302a300506032b6570032100d75a980182b10ab7d54bfed3c964073a" +
                "0ee172f3daa62325af021a68f707511a"
    }
}
