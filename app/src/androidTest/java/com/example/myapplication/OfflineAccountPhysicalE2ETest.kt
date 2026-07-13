package com.example.myapplication

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.core.model.SessionMode
import com.example.myapplication.core.model.SyncState
import com.example.myapplication.data.DemoMelodyRepository
import com.example.myapplication.data.local.CachedAccount
import com.example.myapplication.data.local.MelodyDatabase
import com.example.myapplication.data.local.OfflineAccountStore
import com.example.myapplication.data.local.SecureTokenStore
import com.example.myapplication.data.remote.ApiClient
import com.example.myapplication.data.remote.AuthRepository
import com.example.myapplication.data.remote.OfflineCredentialRequest
import com.example.myapplication.data.remote.jwtSubject
import com.example.myapplication.offlineexchange.ExchangeConnectionState
import com.example.myapplication.offlineexchange.ExchangeCrypto
import com.example.myapplication.offlineexchange.ExchangeMusicCard
import com.example.myapplication.offlineexchange.NearbyExchangeManager
import com.example.myapplication.offlineexchange.OfflineExchangeIdentity
import com.example.myapplication.offlineexchange.OfflineExchangeResult
import com.example.myapplication.offlineexchange.OfflineExchangeSyncScheduler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in physical-device E2E test split into independent instrumentation processes.
 * The host runner invokes prepare -> offline exchange -> online sync on two devices.
 */
@RunWith(AndroidJUnit4::class)
class OfflineAccountPhysicalE2ETest {
    @Test
    fun prepareLoggedInAccountAndOfflineCredential() = runBlocking {
        assumeTrue(argument("e2ePhase") == PREPARE)
        val role = requiredArgument("e2eRole")
        val email = requiredArgument("e2eEmail")
        val password = requiredArgument("e2ePassword")
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val auth = AuthRepository()
        val crypto = ExchangeCrypto().also { it.deleteDeviceKey() }

        val session = auth.signup(
            email = email,
            password = password,
            passwordConfirmation = password,
            displayName = if (role == INITIATOR) "Physical Alpha" else "Physical Beta",
        ).getOrThrow()
        auth.completeOnboarding(
            session.accessToken,
            genres = listOf(if (role == INITIATOR) "Indie" else "Jazz"),
            moods = listOf(if (role == INITIATOR) "Bright" else "Calm"),
        ).getOrThrow()
        val accountId = requireNotNull(jwtSubject(session.accessToken))
        val credential = ApiClient.createOfflineExchangeApi().issueCredential(
            "Bearer ${session.accessToken}",
            OfflineCredentialRequest(crypto.publicKeyBase64()),
        )
        val card = card(role)
        val account = CachedAccount(
            accountId = accountId,
            displayAlias = credential.displayAlias,
            avatarDataUrl = null,
            colorHex = if (role == INITIATOR) 0x6750A4 else 0x006C4C,
            melodyAlias = card.melodyAlias,
            musicCard = card,
            lastAuthenticatedAt = System.currentTimeMillis(),
            offlineCredential = credential,
        )
        SecureTokenStore(context).save(session.accessToken, session.refreshToken)
        OfflineAccountStore(context).save(account)
        ApiClient.configureSession(SecureTokenStore(context)) {}

        assertEquals(accountId, OfflineAccountStore(context).load()?.accountId)
        assertTrue(OfflineAccountStore(context).load()?.canStartOfflineExchange == true)
        assertNotNull(SecureTokenStore(context).load())
        status(
            "e2ePreparedAccountId" to accountId,
            "e2ePreparedCredentialId" to credential.credentialId,
            "e2eRole" to role,
        )
    }

    @Test
    fun restoreOfflineAccountExchangeAndPersistToRoom() = runBlocking {
        assumeTrue(argument("e2ePhase") == EXCHANGE)
        val role = requiredArgument("e2eRole")
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val account = requireNotNull(OfflineAccountStore(context).load())
        val credential = requireNotNull(account.offlineCredential)
        assertTrue(account.canStartOfflineExchange)
        assertNotNull(SecureTokenStore(context).load())
        grantNearbyPermissions(context.packageName)
        val activity = ActivityScenario.launch(MainActivity::class.java)
        val repository = DemoMelodyRepository(context)
        repository.authenticateOffline(account)
        assertEquals(SessionMode.OFFLINE, repository.state.value.sessionMode)

        val completed = CompletableDeferred<OfflineExchangeResult>()
        val crypto = ExchangeCrypto()
        val manager = NearbyExchangeManager(context, crypto) { result ->
            repository.saveOfflineExchange(result, credential.credentialId)
            completed.complete(result)
        }
        var authenticationDigits: String? = null
        val collector = launch {
            manager.state.collect { state ->
                when (state) {
                    is ExchangeConnectionState.EndpointFound -> if (role == INITIATOR) {
                        manager.requestConnection(state.endpointId)
                    }
                    is ExchangeConnectionState.AwaitingApproval -> {
                        authenticationDigits = state.authenticationDigits
                        manager.approveConnection()
                    }
                    is ExchangeConnectionState.Error -> completed.completeExceptionally(
                        AssertionError("Physical offline exchange failed: ${state.message}")
                    )
                    else -> Unit
                }
            }
        }

        try {
            manager.start(
                OfflineExchangeIdentity(
                    ownerUserId = account.accountId,
                    endpointName = "Offline-$role",
                    credential = credential,
                    card = account.musicCard,
                )
            )
            val result = withTimeout(90_000) { completed.await() }
            val database = MelodyDatabase.getInstance(context)
            val stored = withTimeout(15_000) {
                var record = database.offlineExchangeDao().find(account.accountId, result.exchangeId)
                while (record == null) {
                    delay(100)
                    record = database.offlineExchangeDao().find(account.accountId, result.exchangeId)
                }
                record
            }
            assertEquals(SyncState.PENDING.name, stored.syncState)
            assertTrue(
                database.syncOutboxDao().pendingForOwner(account.accountId)
                    .any { it.requestId == result.exchangeId }
            )
            assertTrue(authenticationDigits?.matches(Regex("\\d+")) == true)
            status(
                "e2eAuthenticationDigits" to authenticationDigits.orEmpty(),
                "e2eExchangeId" to result.exchangeId,
                "e2ePayloadHash" to result.payloadHash,
                "e2eLocalSyncState" to stored.syncState,
                "e2eRole" to role,
            )
        } finally {
            manager.stop()
            collector.cancelAndJoin()
            repository.close()
            activity.close()
        }
    }

    @Test
    fun workManagerSyncsAndServerVerifiesProfile() = runBlocking {
        assumeTrue(argument("e2ePhase") == SYNC)
        val role = requiredArgument("e2eRole")
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val account = requireNotNull(OfflineAccountStore(context).load())
        val tokenStore = SecureTokenStore(context)
        val session = requireNotNull(tokenStore.load())
        ApiClient.configureSession(tokenStore) {}
        val database = MelodyDatabase.getInstance(context)
        val original = database.offlineExchangeDao().observeForOwner(account.accountId).first().single()
        val profileApi = ApiClient.createProfileApi()
        val exchangeApi = ApiClient.createOfflineExchangeApi()

        withTimeout(60_000) {
            while (runCatching { profileApi.me("Bearer ${session.accessToken}") }.isFailure) delay(500)
        }
        OfflineExchangeSyncScheduler.enqueue(context)

        val synced = withTimeout(150_000) {
            var record = database.offlineExchangeDao().find(account.accountId, original.id)
            while (record?.syncState != SyncState.SYNCED.name) {
                delay(500)
                record = database.offlineExchangeDao().find(account.accountId, original.id)
            }
            requireNotNull(record)
        }
        val verifiedRemote = withTimeout(90_000) {
            var remote = exchangeApi.history("Bearer ${session.accessToken}")
                .firstOrNull { it.exchangeId == synced.id && it.verificationState == "VERIFIED" }
            while (remote == null) {
                delay(500)
                remote = exchangeApi.history("Bearer ${session.accessToken}")
                    .firstOrNull { it.exchangeId == synced.id && it.verificationState == "VERIFIED" }
            }
            remote
        }
        val profile = withTimeout(60_000) {
            var value = profileApi.me("Bearer ${session.accessToken}")
            while (value.offlineExchangeCount < 1) {
                delay(500)
                value = profileApi.me("Bearer ${session.accessToken}")
            }
            value
        }
        val expectedGenre = if (role == INITIATOR) "Jazz" else "Indie"
        val expectedMood = if (role == INITIATOR) "Calm" else "Bright"
        assertEquals("VERIFIED", verifiedRemote.verificationState)
        assertEquals(SyncState.SYNCED.name, synced.syncState)
        assertTrue(profile.offlineExchangeCount >= 1)
        assertTrue(profile.offlineExchangeGenres.orEmpty().contains(expectedGenre))
        assertTrue(profile.offlineExchangeMoods.orEmpty().contains(expectedMood))
        assertTrue(database.syncOutboxDao().pendingForOwner(account.accountId).isEmpty())
        status(
            "e2eVerifiedExchangeId" to synced.id,
            "e2eVerificationState" to verifiedRemote.verificationState,
            "e2eProfileExchangeCount" to profile.offlineExchangeCount.toString(),
            "e2eProfileGenres" to profile.offlineExchangeGenres.orEmpty().joinToString(","),
            "e2eProfileMoods" to profile.offlineExchangeMoods.orEmpty().joinToString(","),
            "e2eRole" to role,
        )
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

    private fun card(role: String) = ExchangeMusicCard(
        displayAlias = if (role == INITIATOR) "Physical Alpha" else "Physical Beta",
        trackTitle = if (role == INITIATOR) "Offline Alpha" else "Offline Beta",
        trackArtist = if (role == INITIATOR) "Device A" else "Device B",
        melodyAlias = if (role == INITIATOR) "C6 · E6" else "A5 · B5",
        genreTags = listOf(if (role == INITIATOR) "Indie" else "Jazz"),
        moodTags = listOf(if (role == INITIATOR) "Bright" else "Calm"),
    )

    private fun argument(name: String): String? =
        InstrumentationRegistry.getArguments().getString(name)

    private fun requiredArgument(name: String): String =
        requireNotNull(argument(name)) { "Missing instrumentation argument: $name" }

    private fun status(vararg values: Pair<String, String>) {
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
            values.forEach { (key, value) -> putString(key, value) }
        })
    }

    private companion object {
        const val PREPARE = "prepare"
        const val EXCHANGE = "exchange"
        const val SYNC = "sync"
        const val INITIATOR = "initiator"
    }
}
