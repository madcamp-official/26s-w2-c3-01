package com.example.myapplication.data

import java.net.URLEncoder
import java.net.URI
import java.net.URLDecoder

internal data class ResolvedAvatar(
    val seed: String,
    val url: String,
)

/** Keeps profile rendering compatible while older servers roll out DiceBear avatar fields. */
internal object AvatarProfileResolver {
    fun resolve(
        remoteSeed: String?,
        remoteUrl: String?,
        stableIdentity: String?,
        fallbackSeed: String,
    ): ResolvedAvatar {
        val suppliedUrl = remoteUrl.normalized()
        val seed = remoteSeed.normalized()
            ?: suppliedUrl?.diceBearSeed()
            ?: stableIdentity.normalized()
            ?: fallbackSeed.normalized()
            ?: DEFAULT_SEED
        val url = if (suppliedUrl == null || suppliedUrl.isDiceBearUrl()) {
            diceBearUrl(seed)
        } else {
            suppliedUrl
        }
        return ResolvedAvatar(seed = seed, url = url)
    }

    private fun String?.normalized(): String? = this?.trim()?.takeIf(String::isNotEmpty)

    private fun String.isDiceBearUrl(): Boolean =
        startsWith("https://api.dicebear.com/", ignoreCase = true)

    private fun String.diceBearSeed(): String? {
        if (!isDiceBearUrl()) return null
        val rawQuery = runCatching { URI(this).rawQuery }.getOrNull() ?: return null
        val rawSeed = rawQuery
            .split('&')
            .mapNotNull { parameter ->
                val separator = parameter.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                val key = parameter.substring(0, separator)
                parameter.substring(separator + 1).takeIf { key.equals("seed", ignoreCase = true) }
            }
            .firstOrNull()
            ?: return null
        return runCatching { URLDecoder.decode(rawSeed, Charsets.UTF_8.name()) }
            .getOrNull()
            .normalized()
    }

    private fun diceBearUrl(seed: String): String {
        val encodedSeed = URLEncoder.encode(seed, Charsets.UTF_8.name()).replace("+", "%20")
        return "$DICEBEAR_LORELEI_NEUTRAL_URL?seed=$encodedSeed"
    }

    private const val DEFAULT_SEED = "sync-user"
    private const val DICEBEAR_LORELEI_NEUTRAL_URL = "https://api.dicebear.com/10.x/lorelei-neutral/svg"
}
