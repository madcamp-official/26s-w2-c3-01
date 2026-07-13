package com.example.myapplication

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Base64
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.offlineexchange.ExchangeConnectionState
import com.example.myapplication.offlineexchange.ExchangeCrypto
import com.example.myapplication.offlineexchange.ExchangeMusicCard
import com.example.myapplication.offlineexchange.ExchangeProtocol
import com.example.myapplication.offlineexchange.NearbyExchangeManager
import com.example.myapplication.offlineexchange.OfflineCredential
import com.example.myapplication.offlineexchange.OfflineExchangeIdentity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

/**
 * Opt-in two-device test for the real Google Nearby Connections transport.
 *
 * Run this test concurrently on two connected devices, passing `nearbyRole=initiator` to one and
 * `nearbyRole=responder` to the other. Without that argument it is skipped, so a normal single
 * device connectedAndroidTest run remains deterministic.
 */
@RunWith(AndroidJUnit4::class)
class NearbyTwoDeviceExchangeTest {
    @Test
    fun realNearbyTransportCompletesSignedExchange() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val role = InstrumentationRegistry.getArguments().getString("nearbyRole")
        assumeTrue(role == INITIATOR || role == RESPONDER)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        grantNearbyPermissions(context.packageName)
        val activityScenario = ActivityScenario.launch(MainActivity::class.java)

        val crypto = ExchangeCrypto().also { it.deleteDeviceKey() }
        val credential = signedCredential(role!!, crypto.publicKeyBase64())
        val completed = CompletableDeferred<com.example.myapplication.offlineexchange.OfflineExchangeResult>()
        val manager = NearbyExchangeManager(context, crypto) { result -> completed.complete(result) }
        var authenticationDigits: String? = null
        val stateCollector = launch {
            manager.state.collect { state ->
                when (state) {
                    is ExchangeConnectionState.EndpointFound -> if (role == INITIATOR) {
                        manager.requestConnection(state.endpointId)
                    }
                    is ExchangeConnectionState.AwaitingApproval -> {
                        authenticationDigits = state.authenticationDigits
                        instrumentation.sendStatus(2, Bundle().apply {
                            putString("nearbyRole", role)
                            putString("nearbyAuthenticationDigits", state.authenticationDigits)
                        })
                        manager.approveConnection()
                    }
                    is ExchangeConnectionState.Error -> completed.completeExceptionally(
                        AssertionError("Nearby $role failed: ${state.message}")
                    )
                    else -> Unit
                }
            }
        }

        try {
            manager.start(
                OfflineExchangeIdentity(
                    ownerUserId = "test-$role",
                    endpointName = "Nearby-$role",
                    credential = credential,
                    card = card(role),
                )
            )
            val result = withTimeout(60_000) { completed.await() }
            assertTrue(authenticationDigits?.matches(Regex("\\d+")) == true)
            assertEquals(if (role == INITIATOR) "Responder" else "Initiator", result.peerCard.displayAlias)
            assertNotEquals(credential.credentialId, result.peerCredentialId)
            assertTrue(result.payloadHash.matches(Regex("[0-9a-f]{64}")))
            instrumentation.sendStatus(2, Bundle().apply {
                putString("nearbyRole", role)
                putString("nearbyExchangeId", result.exchangeId)
                putString("nearbyPayloadHash", result.payloadHash)
            })
        } finally {
            manager.stop()
            stateCollector.cancelAndJoin()
            crypto.deleteDeviceKey()
            activityScenario.close()
        }
    }

    private fun grantNearbyPermissions(packageName: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val permissions = buildList {
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }
        permissions.forEach { permission ->
            runCatching { automation.grantRuntimePermission(packageName, permission) }
        }
    }

    private fun signedCredential(role: String, devicePublicKey: String): OfflineCredential {
        val now = System.currentTimeMillis()
        val unsigned = OfflineCredential(
            credentialId = if (role == INITIATOR) {
                "11111111-1111-4111-8111-111111111111"
            } else {
                "22222222-2222-4222-8222-222222222222"
            },
            publicSubject = if (role == INITIATOR) {
                "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
            } else {
                "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
            },
            devicePublicKey = devicePublicKey,
            displayAlias = if (role == INITIATOR) "Initiator" else "Responder",
            issuedAt = now - 60_000,
            expiresAt = now + 60 * 60_000,
            serverSignature = "",
            serverPublicKey = Base64.encodeToString(hex(SERVER_PUBLIC_KEY_X509), Base64.NO_WRAP),
        )
        val canonical = ExchangeProtocol.credentialCanonical(unsigned).toByteArray(Charsets.UTF_8)
        val signature = Ed25519Signer().run {
            init(true, Ed25519PrivateKeyParameters(hex(SERVER_PRIVATE_KEY_SEED), 0))
            update(canonical, 0, canonical.size)
            Base64.encodeToString(generateSignature(), Base64.NO_WRAP)
        }
        return unsigned.copy(serverSignature = signature)
    }

    private fun card(role: String) = ExchangeMusicCard(
        displayAlias = if (role == INITIATOR) "Initiator" else "Responder",
        trackTitle = if (role == INITIATOR) "First Song" else "Second Song",
        trackArtist = if (role == INITIATOR) "Artist A" else "Artist B",
        melodyAlias = if (role == INITIATOR) "C6 · E6" else "A5 · B5",
        genreTags = listOf(if (role == INITIATOR) "Indie" else "Jazz"),
        moodTags = listOf(if (role == INITIATOR) "Bright" else "Calm"),
    )

    private fun hex(value: String): ByteArray = value.chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()

    private companion object {
        const val INITIATOR = "initiator"
        const val RESPONDER = "responder"

        // RFC 8032 test key wrapped as PKCS#8/X.509. It is test-only and never used by the app.
        const val SERVER_PRIVATE_KEY_SEED =
            "9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60"
        const val SERVER_PUBLIC_KEY_X509 =
            "302a300506032b6570032100d75a980182b10ab7d54bfed3c964073a" +
                "0ee172f3daa62325af021a68f707511a"
    }
}
