package com.example.myapplication.data

import java.net.URLEncoder

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
        val seed = remoteSeed.normalized()
            ?: stableIdentity.normalized()
            ?: fallbackSeed.normalized()
            ?: DEFAULT_SEED
        val url = remoteUrl.normalized() ?: diceBearUrl(seed)
        return ResolvedAvatar(seed = seed, url = url)
    }

    private fun String?.normalized(): String? = this?.trim()?.takeIf(String::isNotEmpty)

    private fun diceBearUrl(seed: String): String {
        val encodedSeed = URLEncoder.encode(seed, Charsets.UTF_8.name()).replace("+", "%20")
        return "$DICEBEAR_THUMBS_URL?seed=$encodedSeed"
    }

    private const val DEFAULT_SEED = "sync-user"
    private const val DICEBEAR_THUMBS_URL = "https://api.dicebear.com/10.x/thumbs/svg"
}
