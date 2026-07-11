package com.example.myapplication.data.remote

import com.example.myapplication.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private fun retrofit(environment: ApiEnvironment): Retrofit = Retrofit.Builder()
        .baseUrl(environment.apiBaseUrl.trimEnd('/') + "/")
        .addConverterFactory(GsonConverterFactory.create())
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

    fun createMelodyAliasApi(environment: ApiEnvironment = ApiEnvironment()): MelodyAliasApi =
        retrofit(environment).create(MelodyAliasApi::class.java)

    fun createBuildingLoungeApi(environment: ApiEnvironment = ApiEnvironment()): BuildingLoungeApi =
        retrofit(environment).create(BuildingLoungeApi::class.java)

    fun createSocialInteractionApi(environment: ApiEnvironment = ApiEnvironment()): SocialInteractionApi =
        retrofit(environment).create(SocialInteractionApi::class.java)

    fun createChatApi(environment: ApiEnvironment = ApiEnvironment()): ChatApi =
        retrofit(environment).create(ChatApi::class.java)

    fun createLyriaMusicApi(environment: ApiEnvironment = ApiEnvironment()): LyriaMusicApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.MINUTES)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(environment.apiBaseUrl.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LyriaMusicApi::class.java)
    }
}
