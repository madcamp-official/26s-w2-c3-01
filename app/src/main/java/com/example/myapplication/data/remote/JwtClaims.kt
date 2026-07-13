package com.example.myapplication.data.remote

import android.util.Base64
import com.google.gson.JsonParser

fun jwtSubject(token: String): String? = runCatching {
    val payload = token.split('.').getOrNull(1) ?: return@runCatching null
    val json = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))
    JsonParser.parseString(json).asJsonObject.get("sub")?.asString
}.getOrNull()
