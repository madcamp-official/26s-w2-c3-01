package com.melodybubble.server.profile

import org.springframework.stereotype.Component
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Component
class AvatarUrlFactory {
    fun create(seed: String): String =
        "$DICEBEAR_BASE_URL?seed=${URLEncoder.encode(seed, StandardCharsets.UTF_8)}"

    fun create(seed: String, customization: AvatarCustomization): String = buildString {
        append(create(seed))
        append("&eyebrowsVariant=").append(customization.eyebrowsVariant)
        append("&eyesVariant=").append(customization.eyesVariant)
        append("&noseVariant=").append(customization.noseVariant)
        append("&mouthVariant=").append(customization.mouthVariant)
        if (customization.glassesVariant == null) append("&glassesProbability=0")
        else append("&glassesVariant=").append(customization.glassesVariant).append("&glassesProbability=100")
        append("&frecklesProbability=").append(if (customization.freckles) 100 else 0)
    }

    fun resolve(seed: String, storedUrl: String?): String = storedUrl?.trim()?.takeIf(String::isNotEmpty) ?: create(seed)

    private companion object {
        const val DICEBEAR_BASE_URL = "https://api.dicebear.com/10.x/lorelei-neutral/svg"
    }
}

data class AvatarCustomization(
    val eyebrowsVariant: String,
    val eyesVariant: String,
    val noseVariant: String,
    val mouthVariant: String,
    val glassesVariant: String? = null,
    val freckles: Boolean = false,
) {
    fun validated(): AvatarCustomization {
        require(eyebrowsVariant in EYEBROWS) { "Invalid eyebrows variant" }
        require(eyesVariant in EYES) { "Invalid eyes variant" }
        require(noseVariant in NOSES) { "Invalid nose variant" }
        require(mouthVariant in MOUTHS) { "Invalid mouth variant" }
        require(glassesVariant == null || glassesVariant in GLASSES) { "Invalid glasses variant" }
        return this
    }

    companion object {
        val EYEBROWS = (1..13).map { "variant%02d".format(it) }.toSet()
        val EYES = (1..24).map { "variant%02d".format(it) }.toSet()
        val NOSES = (1..6).map { "variant%02d".format(it) }.toSet()
        val MOUTHS = ((1..18).map { "happy%02d".format(it) } + (1..9).map { "sad%02d".format(it) }).toSet()
        val GLASSES = (1..5).map { "variant%02d".format(it) }.toSet()
    }
}
