package com.melodybubble.server.offlineexchange

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.UUID

class OfflineCredentialSignerTest {
    @Test
    fun `same configured secret keeps the offline trust key stable across restarts`() {
        val secret = "a-test-secret-that-is-long-enough-for-repeatable-signing"

        assertEquals(
            OfflineCredentialSigner(secret).publicKey,
            OfflineCredentialSigner(secret).publicKey,
        )
    }

    @Test
    fun `public subject is stable per account without exposing the account id`() {
        val signer = OfflineCredentialSigner("a-test-secret-that-is-long-enough-for-repeatable-signing")
        val user = UUID.randomUUID()

        assertEquals(signer.publicSubject(user), signer.publicSubject(user))
        assertTrue(signer.publicSubject(user) != user)
        assertTrue(signer.publicSubject(user) != signer.publicSubject(UUID.randomUUID()))
    }

    @Test
    fun `issued credential signatures are verifiable with the advertised server key`() {
        val signer = OfflineCredentialSigner("a-test-secret-that-is-long-enough-for-repeatable-signing")
        val canonical = "credential|subject|device-key|alias|100|200"
        val publicKey = KeyFactory.getInstance("Ed25519").generatePublic(
            X509EncodedKeySpec(Base64.getDecoder().decode(signer.publicKey))
        )

        val verified = Signature.getInstance("Ed25519").run {
            initVerify(publicKey)
            update(canonical.toByteArray(StandardCharsets.UTF_8))
            verify(Base64.getDecoder().decode(signer.sign(canonical)))
        }

        assertTrue(verified)
    }
}
