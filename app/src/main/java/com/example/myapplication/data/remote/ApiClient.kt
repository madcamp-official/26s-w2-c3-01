package com.example.myapplication.data.remote

import com.example.myapplication.BuildConfig
import com.example.myapplication.data.local.SecureTokenStore
import com.google.gson.Gson
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArraySet

object ApiClient {
    fun configureSession(tokenStore: SecureTokenStore, onSessionExpired: () -> Unit) {
        SessionRuntime.configure(tokenStore, onSessionExpired)
    }

    fun addAccessTokenListener(listener: (String) -> Unit): () -> Unit =
        SessionRuntime.addAccessTokenListener(listener)

    fun addSessionExpiredListener(listener: () -> Unit) {
        SessionRuntime.addSessionExpiredListener(listener)
    }

    fun invalidateSession() {
        SessionRuntime.invalidate()
    }

    private fun retrofit(environment: ApiEnvironment): Retrofit = Retrofit.Builder()
        .baseUrl(environment.apiBaseUrl.trimEnd('/') + "/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private fun authenticatedRetrofit(environment: ApiEnvironment): Retrofit = Retrofit.Builder()
        .baseUrl(environment.apiBaseUrl.trimEnd('/') + "/")
        .client(authenticatedClient(environment))
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private fun authenticatedClient(environment: ApiEnvironment, builder: OkHttpClient.Builder = OkHttpClient.Builder()): OkHttpClient =
        builder
            .addInterceptor { chain ->
                val token = SessionRuntime.accessToken()
                val request = if (token != null && chain.request().header("Authorization") != null) {
                    chain.request().newBuilder().header("Authorization", "Bearer $token").build()
                } else chain.request()
                chain.proceed(request)
            }
            .authenticator(SessionAuthenticator(environment))
            .build()

    fun createAuthApi(environment: ApiEnvironment = ApiEnvironment()): AuthApi {
        val isLocalDebug = BuildConfig.DEBUG && (
            environment.apiBaseUrl.startsWith("http://localhost") ||
                environment.apiBaseUrl.startsWith("http://10.0.2.2")
            )
        require(environment.apiBaseUrl.startsWith("https://") || isLocalDebug) {
            "API_BASE_URL must use HTTPS."
        }
        return retrofit(environment).create(AuthApi::class.java)
    }

    fun createMusicSearchApi(): MusicSearchApi = Retrofit.Builder()
        .baseUrl("https://itunes.apple.com/")
        .client(
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
        )
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(MusicSearchApi::class.java)

    fun createDeezerArtistApi(): DeezerArtistApi = Retrofit.Builder()
        .baseUrl("https://api.deezer.com/")
        .client(
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
        )
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(DeezerArtistApi::class.java)

    fun createNearbyApi(environment: ApiEnvironment = ApiEnvironment()): NearbyApi =
        authenticatedRetrofit(environment).create(NearbyApi::class.java)

    fun createProfileApi(environment: ApiEnvironment = ApiEnvironment()): ProfileApi =
        authenticatedRetrofit(environment).create(ProfileApi::class.java)

    fun createSocialApi(environment: ApiEnvironment = ApiEnvironment()): SocialApi =
        authenticatedRetrofit(environment).create(SocialApi::class.java)

    fun createOfflineExchangeApi(environment: ApiEnvironment = ApiEnvironment()): OfflineExchangeApi =
        authenticatedRetrofit(environment).create(OfflineExchangeApi::class.java)

    fun createBuildingLoungeApi(environment: ApiEnvironment = ApiEnvironment()): BuildingLoungeApi =
        retrofit(environment).create(BuildingLoungeApi::class.java)

    fun createLocationLoungeApi(environment: ApiEnvironment = ApiEnvironment()): LocationLoungeApi =
        authenticatedRetrofit(environment).create(LocationLoungeApi::class.java)

}

private object SessionRuntime {
    @Volatile private var store: SecureTokenStore? = null
    @Volatile private var expiredCallback: (() -> Unit)? = null
    private val refreshLock = Any()
    private val accessTokenListeners = CopyOnWriteArraySet<(String) -> Unit>()
    private val sessionExpiredListeners = CopyOnWriteArraySet<() -> Unit>()

    fun configure(tokenStore: SecureTokenStore, onSessionExpired: () -> Unit) {
        store = tokenStore
        expiredCallback = onSessionExpired
    }

    fun accessToken(): String? = store?.load()?.accessToken

    fun addAccessTokenListener(listener: (String) -> Unit): () -> Unit {
        accessTokenListeners += listener
        return { accessTokenListeners -= listener }
    }

    fun addSessionExpiredListener(listener: () -> Unit) {
        sessionExpiredListeners += listener
    }

    fun invalidate() {
        store?.let(::expire)
    }

    fun refresh(environment: ApiEnvironment, rejectedAccessToken: String?): String? = synchronized(refreshLock) {
        val tokenStore = store ?: return@synchronized null
        val current = tokenStore.load() ?: return@synchronized null
        if (rejectedAccessToken != null && current.accessToken != rejectedAccessToken) return@synchronized current.accessToken
        // Production issues refreshable sessions. An old access-only session cannot recover from
        // a 401, so surface reauthentication instead of leaving nearby sharing in a fake retry loop.
        val refreshToken = current.refreshToken ?: return@synchronized expire(tokenStore)
        val body = Gson().toJson(RefreshRequest(refreshToken)).toRequestBody("application/json".toMediaType())
        val request = okhttp3.Request.Builder()
            .url(environment.apiBaseUrl.trimEnd('/') + "/api/v1/auth/refresh")
            .post(body)
            .build()
        val response = runCatching { OkHttpClient().newCall(request).execute() }.getOrNull()
            ?: return@synchronized null
        response.use {
            if (!it.isSuccessful) return@synchronized expire(tokenStore)
            val session = runCatching { Gson().fromJson(it.body?.charStream(), TokenResponse::class.java) }.getOrNull()
                ?: return@synchronized expire(tokenStore)
            tokenStore.save(session.accessToken, session.refreshToken ?: refreshToken)
            accessTokenListeners.forEach { listener ->
                runCatching { listener(session.accessToken) }
            }
            session.accessToken
        }
    }

    private fun expire(tokenStore: SecureTokenStore): String? {
        tokenStore.clear()
        expiredCallback?.invoke()
        sessionExpiredListeners.forEach { listener -> runCatching(listener) }
        return null
    }
}

private class SessionAuthenticator(private val environment: ApiEnvironment) : Authenticator {
    override fun authenticate(route: Route?, response: Response): okhttp3.Request? {
        if (response.priorResponse != null) return null
        val rejected = response.request.header("Authorization")?.removePrefix("Bearer ")
        val renewed = SessionRuntime.refresh(environment, rejected) ?: return null
        return response.request.newBuilder().header("Authorization", "Bearer $renewed").build()
    }
}
