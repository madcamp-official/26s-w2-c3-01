package com.example.myapplication.data

import java.net.URLEncoder
import java.net.URI
import java.net.URLDecoder

internal data class ResolvedAvatar(
    val seed: String,
    val url: String,
)

data class AvatarCustomization(
    val eyebrowsVariant: String = "variant01",
    val eyesVariant: String = "variant01",
    val noseVariant: String = "variant01",
    val mouthVariant: String = "happy01",
    val glassesVariant: String? = null,
    val freckles: Boolean = false,
) {
    companion object {
        val eyebrows = (1..13).map { "variant%02d".format(it) }
        val eyes = (1..24).map { "variant%02d".format(it) }
        val noses = (1..6).map { "variant%02d".format(it) }
        val mouths = (1..18).map { "happy%02d".format(it) } + (1..9).map { "sad%02d".format(it) }
        val glasses = (1..5).map { "variant%02d".format(it) }
    }
}

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
        val url = when {
            suppliedUrl == null -> diceBearUrl(seed)
            suppliedUrl.isCurrentLoreleiNeutralUrl() -> suppliedUrl
            suppliedUrl.isDiceBearUrl() -> diceBearUrl(seed)
            else -> suppliedUrl
        }
        return ResolvedAvatar(seed = seed, url = url)
    }

    fun customizationFrom(url: String?): AvatarCustomization {
        val parameters = url.normalized()?.queryParameters().orEmpty()
        return AvatarCustomization(
            eyebrowsVariant = parameters["eyebrowsVariant"].takeIf { it in AvatarCustomization.eyebrows }
                ?: AvatarCustomization.eyebrows.first(),
            eyesVariant = parameters["eyesVariant"].takeIf { it in AvatarCustomization.eyes }
                ?: AvatarCustomization.eyes.first(),
            noseVariant = parameters["noseVariant"].takeIf { it in AvatarCustomization.noses }
                ?: AvatarCustomization.noses.first(),
            mouthVariant = parameters["mouthVariant"].takeIf { it in AvatarCustomization.mouths }
                ?: AvatarCustomization.mouths.first(),
            glassesVariant = parameters["glassesVariant"].takeIf { it in AvatarCustomization.glasses },
            freckles = parameters["frecklesProbability"] == "100",
        )
    }

    fun customizedUrl(seed: String, customization: AvatarCustomization): String = buildString {
        append(diceBearUrl(seed))
        append("&eyebrowsVariant=").append(customization.eyebrowsVariant)
        append("&eyesVariant=").append(customization.eyesVariant)
        append("&noseVariant=").append(customization.noseVariant)
        append("&mouthVariant=").append(customization.mouthVariant)
        customization.glassesVariant?.let {
            append("&glassesVariant=").append(it).append("&glassesProbability=100")
        } ?: append("&glassesProbability=0")
        append("&frecklesProbability=").append(if (customization.freckles) 100 else 0)
    }

    private fun String?.normalized(): String? = this?.trim()?.takeIf(String::isNotEmpty)

    private fun String.isDiceBearUrl(): Boolean =
        startsWith("https://api.dicebear.com/", ignoreCase = true)

    private fun String.isCurrentLoreleiNeutralUrl(): Boolean =
        startsWith(DICEBEAR_LORELEI_NEUTRAL_URL, ignoreCase = true)

    private fun String.queryParameters(): Map<String, String> = runCatching {
        URI(this).rawQuery.orEmpty().split('&').mapNotNull { parameter ->
            val separator = parameter.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            URLDecoder.decode(parameter.substring(0, separator), Charsets.UTF_8.name()) to
                URLDecoder.decode(parameter.substring(separator + 1), Charsets.UTF_8.name())
        }.toMap()
    }.getOrDefault(emptyMap())

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
