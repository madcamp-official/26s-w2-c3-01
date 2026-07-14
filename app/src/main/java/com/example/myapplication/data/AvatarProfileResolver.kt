package com.example.myapplication.data

import java.net.URLEncoder
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

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
        return explicitCustomizationFrom(url) ?: AvatarCustomization()
    }

    /**
     * Returns only a fully specified customization. A seed-only DiceBear URL deliberately returns
     * null because DiceBear chooses its visible parts from the seed; treating it as variant01 would
     * make the editor disagree with the avatar currently on screen.
     */
    fun explicitCustomizationFrom(url: String?): AvatarCustomization? {
        val parameters = url.normalized()?.queryParameters().orEmpty()
        val eyebrows = parameters["eyebrowsVariant"].takeIf { it in AvatarCustomization.eyebrows }
            ?: return null
        val eyes = parameters["eyesVariant"].takeIf { it in AvatarCustomization.eyes }
            ?: return null
        val nose = parameters["noseVariant"].takeIf { it in AvatarCustomization.noses }
            ?: return null
        val mouth = parameters["mouthVariant"].takeIf { it in AvatarCustomization.mouths }
            ?: return null
        val glasses = parameters["glassesVariant"]
            .takeIf { parameters["glassesProbability"] != "0" && it in AvatarCustomization.glasses }
        return AvatarCustomization(
            eyebrowsVariant = eyebrows,
            eyesVariant = eyes,
            noseVariant = nose,
            mouthVariant = mouth,
            glassesVariant = glasses,
            freckles = parameters["frecklesProbability"] == "100",
        )
    }

    /** Reads the exact parts selected by DiceBear for legacy seed-only avatar URLs. */
    fun customizationFromSvg(svg: String?): AvatarCustomization? {
        if (svg.isNullOrBlank()) return null
        val parts = AVATAR_PART_PATTERN.findAll(svg).associate { match ->
            match.groupValues[1] to match.groupValues[2]
        }
        val eyebrows = parts["eyebrows"].takeIf { it in AvatarCustomization.eyebrows } ?: return null
        val eyes = parts["eyes"].takeIf { it in AvatarCustomization.eyes } ?: return null
        val nose = parts["nose"].takeIf { it in AvatarCustomization.noses } ?: return null
        val mouth = parts["mouth"].takeIf { it in AvatarCustomization.mouths } ?: return null
        return AvatarCustomization(
            eyebrowsVariant = eyebrows,
            eyesVariant = eyes,
            noseVariant = nose,
            mouthVariant = mouth,
            glassesVariant = parts["glasses"].takeIf { it in AvatarCustomization.glasses },
            freckles = parts.containsKey("freckles"),
        )
    }

    suspend fun loadCurrentCustomization(seed: String, url: String?): AvatarCustomization? {
        val currentUrl = currentUrl(seed, url)
        explicitCustomizationFrom(currentUrl)?.let { return it }
        if (!currentUrl.isCurrentLoreleiNeutralUrl()) return null
        val svg = withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(currentUrl)
                .header("Accept", "image/svg+xml")
                .get()
                .build()
            runCatching {
                avatarHttpClient.newCall(request).execute().use { response ->
                    response.body.takeIf { response.isSuccessful }?.string()
                }
            }.getOrNull()
        }
        return customizationFromSvg(svg)
    }

    fun currentUrl(seed: String, url: String?): String = resolve(
        remoteSeed = seed,
        remoteUrl = url,
        stableIdentity = seed,
        fallbackSeed = seed,
    ).url

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
    private val AVATAR_PART_PATTERN = Regex(
        """id=[\"'](eyebrows|eyes|nose|mouth|glasses|freckles)-((?:variant|happy|sad)\d{2})-[^\"']+[\"']""",
    )
    private val avatarHttpClient = OkHttpClient.Builder()
        .callTimeout(8, TimeUnit.SECONDS)
        .build()
}
