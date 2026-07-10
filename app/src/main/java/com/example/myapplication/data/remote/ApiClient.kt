package com.example.myapplication.data.remote

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    fun createAuthApi(environment: ApiEnvironment = ApiEnvironment()): AuthApi {
        require(environment.apiBaseUrl.startsWith("https://")) {
            "API_BASE_URL must use HTTPS."
        }
        return Retrofit.Builder()
            .baseUrl(environment.apiBaseUrl.trimEnd('/') + "/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApi::class.java)
    }
}
