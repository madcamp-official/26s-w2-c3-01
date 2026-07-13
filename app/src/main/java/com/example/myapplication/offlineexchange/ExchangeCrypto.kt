package com.example.myapplication.offlineexchange

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.UUID
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

class ExchangeCrypto {
    private val keyAlias = "melody-bubble-offline-exchange-device"

    fun publicKeyBase64(): String {
        ensureDeviceKey()
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return Base64.encodeToString(keyStore.getCertificate(keyAlias).publicKey.encoded, Base64.NO_WRAP)
    }

    fun sign(value: String): String {
        ensureDeviceKey()
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val privateKey = keyStore.getKey(keyAlias, null) as java.security.PrivateKey
        return Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(value.toByteArray(StandardCharsets.UTF_8))
            Base64.encodeToString(sign(), Base64.NO_WRAP)
        }
    }

    fun verifyDevice(publicKeyBase64: String, value: String, signatureBase64: String): Boolean = runCatching {
        val publicKey = KeyFactory.getInstance("EC").generatePublic(
            X509EncodedKeySpec(Base64.decode(publicKeyBase64, Base64.NO_WRAP))
        )
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(publicKey)
            update(value.toByteArray(StandardCharsets.UTF_8))
            verify(Base64.decode(signatureBase64, Base64.NO_WRAP))
        }
    }.getOrDefault(false)

    fun verifyServer(credential: OfflineCredential, trustedServerPublicKey: String): Boolean = runCatching {
        if (credential.serverPublicKey != trustedServerPublicKey) return@runCatching false
        val encodedPublicKey = Base64.decode(trustedServerPublicKey, Base64.NO_WRAP)
        if (encodedPublicKey.size != ED25519_X509_PREFIX.size + 32 ||
            !encodedPublicKey.copyOfRange(0, ED25519_X509_PREFIX.size)
                .contentEquals(ED25519_X509_PREFIX)
        ) return@runCatching false
        val message = ExchangeProtocol.credentialCanonical(credential)
            .toByteArray(StandardCharsets.UTF_8)
        Ed25519Signer().run {
            init(
                false,
                Ed25519PublicKeyParameters(encodedPublicKey, ED25519_X509_PREFIX.size),
            )
            update(message, 0, message.size)
            verifySignature(Base64.decode(credential.serverSignature, Base64.NO_WRAP))
        }
    }.getOrDefault(false)

    fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    fun deterministicExchangeId(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
        val uuidBytes = bytes.copyOfRange(0, 16)
        uuidBytes[6] = ((uuidBytes[6].toInt() and 0x0f) or 0x40).toByte()
        uuidBytes[8] = ((uuidBytes[8].toInt() and 0x3f) or 0x80).toByte()
        val buffer = java.nio.ByteBuffer.wrap(uuidBytes)
        return UUID(buffer.long, buffer.long).toString()
    }

    fun deleteDeviceKey() {
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
            if (containsAlias(keyAlias)) deleteEntry(keyAlias)
        }
    }

    private fun ensureDeviceKey() {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.containsAlias(keyAlias)) return
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
            initialize(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                )
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
                    .build()
            )
            generateKeyPair()
        }
    }

    private companion object {
        val ED25519_X509_PREFIX = byteArrayOf(
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00,
        )
    }
}
