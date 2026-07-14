package com.example.myapplication.music

import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/**
 * Opens a track in one concrete music application.
 *
 * Android has no universal "default streaming service" setting. We therefore prefer the app
 * that most recently exposed active playback to Sync, then the device's default media-search
 * handler, and finally another installed media-search handler. Every successful media launch is
 * package/component scoped so Android does not show a browser/video-app chooser.
 */
object MusicAppLauncher {
    fun openTrack(
        context: Context,
        title: String,
        artist: String,
        externalUrl: String? = null,
    ): Boolean {
        val normalizedTitle = title.trim()
        val normalizedArtist = artist.trim()
        val query = listOf(normalizedTitle, normalizedArtist)
            .filter(String::isNotBlank)
            .joinToString(" ")
        if (query.isBlank()) return false

        val searchIntents = mediaSearchIntents(query, normalizedTitle, normalizedArtist)
        val resolvedTargets = searchIntents.flatMapIndexed { actionPriority, intent ->
            context.packageManager.queryActivities(intent).mapNotNull { result ->
                val info = result.activityInfo ?: return@mapNotNull null
                info.packageName
                    .takeUnless(::isUnsuitableMediaPackage)
                    ?.let { ResolvedMusicTarget(it, info.name, actionPriority, intent) }
            }
        }
        val defaultPackage = searchIntents.asSequence()
            .mapNotNull { context.packageManager.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY) }
            .mapNotNull { it.activityInfo?.packageName }
            .firstOrNull { !isUnsuitableMediaPackage(it) }
        val lastPlaybackPackage = MusicPlaybackAppPreference.lastPackage(context)
        val installedLastPlaybackPackage = lastPlaybackPackage?.takeIf {
            context.packageManager.getLaunchIntentForPackage(it) != null
        }
        val orderedPackages = orderedMusicPackages(
            lastPlaybackPackage = lastPlaybackPackage,
            defaultPackage = defaultPackage,
            candidates = resolvedTargets.map(ResolvedMusicTarget::packageName) +
                installedKnownPackages(context) +
                listOfNotNull(installedLastPlaybackPackage),
        )

        // Prefer a real app that claims the supplied store URL, but never a browser or the regular
        // YouTube video app. This preserves exact track links when their matching music app exists.
        val nativeUrlTargets = externalUrl
            ?.takeIf(::isSafeWebUrl)
            ?.let { url ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
                context.packageManager.queryActivities(intent).mapNotNull { result ->
                    val info = result.activityInfo ?: return@mapNotNull null
                    info.packageName
                        .takeIf(orderedPackages::contains)
                        ?.let { ResolvedMusicTarget(it, info.name, -1, intent) }
                }
            }
            .orEmpty()

        val targets = (nativeUrlTargets + resolvedTargets)
            .distinctBy { Triple(it.packageName, it.className, it.actionPriority) }
            .sortedWith(
                compareBy<ResolvedMusicTarget> {
                    orderedPackages.indexOf(it.packageName).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE
                }.thenBy(ResolvedMusicTarget::actionPriority)
            )
        for (target in targets) {
            if (context.launch(target.intent, ComponentName(target.packageName, target.className))) {
                MusicPlaybackAppPreference.remember(context, target.packageName)
                return true
            }
        }

        // A known player may intentionally omit searchable activities. Package-scoped search/deep
        // links are still safe to try, and its launcher activity is preferable to a web chooser.
        for (packageName in orderedPackages) {
            for (intent in packageSearchIntents(packageName, query) + searchIntents) {
                if (context.launch(Intent(intent).setPackage(packageName))) {
                    MusicPlaybackAppPreference.remember(context, packageName)
                    return true
                }
            }
            context.packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
                if (context.launch(launchIntent)) {
                    MusicPlaybackAppPreference.remember(context, packageName)
                    return true
                }
            }
        }

        // No suitable installed music app was discoverable. Keep a web fallback so the gesture is
        // never a dead end; a chooser is acceptable only in this final fallback branch.
        val fallbackUrl = externalUrl
            ?.takeIf(::isSafeWebUrl)
            ?: "https://music.youtube.com/search?q=${Uri.encode(query)}"
        return context.launch(Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)))
    }

    private fun mediaSearchIntents(query: String, title: String, artist: String): List<Intent> =
        listOf(
            Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH),
            Intent(MediaStore.INTENT_ACTION_MEDIA_SEARCH),
        ).map { intent ->
            intent
                .putExtra(SearchManager.QUERY, query)
                .putExtra(MediaStore.EXTRA_MEDIA_TITLE, title)
                .putExtra(MediaStore.EXTRA_MEDIA_ARTIST, artist)
        }

    private fun packageSearchIntents(packageName: String, query: String): List<Intent> = when (packageName) {
        SPOTIFY_PACKAGE -> listOf(Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:${Uri.encode(query)}")))
        YOUTUBE_MUSIC_PACKAGE -> listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/search?q=${Uri.encode(query)}"))
                .addCategory(Intent.CATEGORY_BROWSABLE)
        )
        APPLE_MUSIC_PACKAGE -> listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://music.apple.com/kr/search?term=${Uri.encode(query)}"))
                .addCategory(Intent.CATEGORY_BROWSABLE)
        )
        else -> emptyList()
    }

    private fun installedKnownPackages(context: Context): List<String> = KNOWN_MUSIC_PACKAGES.filter {
        context.packageManager.getLaunchIntentForPackage(it) != null
    }

    private fun PackageManager.queryActivities(intent: Intent) = if (Build.VERSION.SDK_INT >= 33) {
        queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
    } else {
        @Suppress("DEPRECATION")
        queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
    }

    private fun Context.launch(intent: Intent, component: ComponentName? = null): Boolean = runCatching {
        startActivity(
            Intent(intent)
                .apply { if (component != null) setComponent(component) }
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.isSuccess

    private data class ResolvedMusicTarget(
        val packageName: String,
        val className: String,
        val actionPriority: Int,
        val intent: Intent,
    )
}

object MusicPlaybackAppPreference {
    fun remember(context: Context, packageName: String) {
        if (packageName == context.packageName || isUnsuitableMediaPackage(packageName)) return
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_PLAYBACK_PACKAGE, packageName)
            .apply()
    }

    fun lastPackage(context: Context): String? =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_PLAYBACK_PACKAGE, null)
            ?.takeUnless(::isUnsuitableMediaPackage)

    private const val PREFERENCES_NAME = "sync-music-app"
    private const val KEY_LAST_PLAYBACK_PACKAGE = "last-playback-package"
}

internal fun orderedMusicPackages(
    lastPlaybackPackage: String?,
    defaultPackage: String?,
    candidates: List<String>,
): List<String> {
    val available = candidates.filterNot(::isUnsuitableMediaPackage).toSet()
    return buildList {
        lastPlaybackPackage?.takeIf(available::contains)?.let(::add)
        defaultPackage?.takeIf(available::contains)?.let(::add)
        KNOWN_MUSIC_PACKAGES.filterTo(this, available::contains)
        candidates.filterTo(this) { it in available }
    }.distinct()
}

/**
 * MediaSession metadata does not identify whether the media is music or video. Restrict automatic
 * presence detection to packages that are known to be music-first players; this prevents regular
 * YouTube, browsers, streaming-video apps, and gallery players from appearing as "듣는 중".
 */
internal fun isRecognizedMusicPlaybackPackage(packageName: String): Boolean =
    packageName.lowercase() in RECOGNIZED_MUSIC_PLAYBACK_PACKAGES

private fun isSafeWebUrl(url: String): Boolean = runCatching {
    Uri.parse(url).scheme.equals("https", ignoreCase = true)
}.getOrDefault(false)

private fun isUnsuitableMediaPackage(packageName: String): Boolean {
    val normalized = packageName.lowercase()
    return normalized in NON_MUSIC_PACKAGES ||
        "browser" in normalized ||
        ("youtube" in normalized && normalized != YOUTUBE_MUSIC_PACKAGE)
}

private const val SPOTIFY_PACKAGE = "com.spotify.music"
private const val YOUTUBE_MUSIC_PACKAGE = "com.google.android.apps.youtube.music"
private const val APPLE_MUSIC_PACKAGE = "com.apple.android.music"

internal val KNOWN_MUSIC_PACKAGES = listOf(
    SPOTIFY_PACKAGE,
    YOUTUBE_MUSIC_PACKAGE,
    APPLE_MUSIC_PACKAGE,
    "com.iloen.melon",
    "com.ktmusic.geniemusic",
    "skplanet.musicmate",
    "com.neowiz.android.bugs",
    "com.nhn.android.music",
    "com.nhn.android.vibe",
    "com.dreamus.flo",
    "com.sec.android.app.music",
    "com.amazon.mp3",
    "com.aspiro.tidal",
    "deezer.android.app",
    "com.soundcloud.android",
)

private val RECOGNIZED_MUSIC_PLAYBACK_PACKAGES = KNOWN_MUSIC_PACKAGES
    .map(String::lowercase)
    .toSet()

private val NON_MUSIC_PACKAGES = setOf(
    "android",
    "com.android.chrome",
    "com.chrome.beta",
    "com.chrome.canary",
    "com.google.android.youtube",
    "com.microsoft.emmx",
    "com.sec.android.app.sbrowser",
    "org.mozilla.firefox",
)
