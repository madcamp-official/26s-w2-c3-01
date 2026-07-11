package com.example.myapplication.data.remote

import com.example.myapplication.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

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

    fun createLyriaMusicApi(environment: ApiEnvironment = ApiEnvironment()): LyriaMusicApi =
        retrofit(environment).create(LyriaMusicApi::class.java)
}
