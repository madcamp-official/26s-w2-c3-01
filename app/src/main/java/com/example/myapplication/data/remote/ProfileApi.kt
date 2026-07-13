package com.example.myapplication.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.PUT

data class RemoteTasteMetric(val label: String, val count: Int, val ratio: Double)
data class RemoteTasteFingerprint(
    val genres: List<RemoteTasteMetric>? = emptyList(),
    val moods: List<RemoteTasteMetric>? = emptyList(),
)
data class RemoteProfileStats(
    val followingCount: Int,
    val followerCount: Int,
    val verifiedExchangeCount: Int,
    val uniqueExchangeUserCount: Int,
    val receivedCardCount: Int,
)
data class RemoteProfileMelodyAlias(
    val id: String,
    val notes: List<String>,
    val tone: String,
    val mood: String,
    val tempo: Int,
)

data class RemoteProfileTrack(
    val rank: Int,
    val provider: String = "MANUAL",
    val providerTrackId: String? = null,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val genreTags: List<String>? = emptyList(),
    val moodTags: List<String>? = emptyList(),
)

data class RemoteProfileArtist(
    val rank: Int,
    val provider: String = "MANUAL",
    val providerArtistId: String? = null,
    val name: String,
    val imageUrl: String? = null,
    val genreTags: List<String>? = emptyList(),
)

data class RemoteProfilePrivacy(
    val currentMusicVisibility: String = "EVERYONE",
    val listeningInsightsEnabled: Boolean = false,
    val listeningInsightsVisibility: String = "PRIVATE",
    val exchangeInsightsVisibility: String = "EXCHANGED",
    val bubblePresenceVisibility: String = "PARTICIPANTS_ONLY",
)

data class RemoteProfileNowPlaying(
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val isPlaying: Boolean,
    val durationMs: Long? = null,
    val positionMs: Long? = null,
    val positionObservedAt: String? = null,
    val observedAt: String,
    val expiresAt: String,
)

data class RemoteCommonTasteMetric(
    val label: String,
    val type: String,
    val score: Int,
    val evidenceCount: Int,
)

data class RemoteCommonTasteSummary(
    val score: Int,
    val metrics: List<RemoteCommonTasteMetric>? = emptyList(),
    val algorithmVersion: String,
    val sampleSize: Int,
    val calculatedAt: String,
)

data class RemoteProfile(
    val profileHandle: String?,
    val displayName: String,
    val profileColor: String,
    val bio: String?,
    val avatarDataUrl: String?,
    val profileMusicUrl: String?,
    val profileMusicDescription: String?,
    val genres: List<String>?,
    val moods: List<String>?,
    val discoverable: Boolean,
    val shareMusic: Boolean,
    val offlineExchangeEnabled: Boolean?,
    val melodyAlias: RemoteProfileMelodyAlias?,
    val stats: RemoteProfileStats?,
    val tasteFingerprint: RemoteTasteFingerprint?,
    val offlineExchangeCount: Int = 0,
    val offlineExchangeGenres: List<String>? = emptyList(),
    val offlineExchangeMoods: List<String>? = emptyList(),
    val profileRevision: Long = 0,
    val signatureTracks: List<RemoteProfileTrack>? = emptyList(),
    val favoriteArtists: List<RemoteProfileArtist>? = emptyList(),
    val privacy: RemoteProfilePrivacy? = null,
)

data class RemotePublicProfile(
    val profileHandle: String,
    val displayName: String,
    val profileColor: String,
    val bio: String?,
    val avatarUrl: String?,
    val profileMusicUrl: String?,
    val profileMusicDescription: String?,
    val genres: List<String>?,
    val moods: List<String>?,
    val melodyAlias: RemoteProfileMelodyAlias?,
    val stats: RemoteProfileStats,
    val tasteFingerprint: RemoteTasteFingerprint?,
    val relationship: String,
    val following: Boolean,
    val mutual: Boolean,
    val sharedVerifiedExchangeCount: Int,
    val signatureTracks: List<RemoteProfileTrack>? = emptyList(),
    val favoriteArtists: List<RemoteProfileArtist>? = emptyList(),
    val nowPlaying: RemoteProfileNowPlaying? = null,
    val commonTaste: RemoteCommonTasteSummary? = null,
    val sectionStates: Map<String, String>? = emptyMap(),
)
data class ProfileUpdateRequest(
    val displayName: String,
    val profileColor: String,
    val bio: String,
    val avatarDataUrl: String?,
    val genres: List<String>,
    val moods: List<String>,
)
data class PrivacyUpdateRequest(
    val discoverable: Boolean,
    val shareMusic: Boolean,
    val offlineExchangeEnabled: Boolean,
)
data class ProfileCurationUpdateRequest(
    val signatureTracks: List<RemoteProfileTrack>,
    val favoriteArtists: List<RemoteProfileArtist>,
    val profileRevision: Long,
)
data class ProfilePrivacyUpdateRequest(
    val currentMusicVisibility: String,
    val listeningInsightsEnabled: Boolean,
    val listeningInsightsVisibility: String,
    val exchangeInsightsVisibility: String,
    val bubblePresenceVisibility: String,
)
data class ProfileMusicUpdateRequest(val candidateKey: String, val description: String?)
data class MelodyAliasUpdateRequest(
    val id: String,
    val notes: List<String>,
    val tone: String,
    val mood: String,
    val tempo: Int,
)

interface ProfileApi {
    @GET("api/v1/me") suspend fun me(@Header("Authorization") authorization: String): RemoteProfile
    @PATCH("api/v1/me") suspend fun update(
        @Header("Authorization") authorization: String,
        @Body request: ProfileUpdateRequest,
    ): RemoteProfile
    @PUT("api/v1/me/privacy") suspend fun privacy(
        @Header("Authorization") authorization: String,
        @Body request: PrivacyUpdateRequest,
    ): RemoteProfile
    @PUT("api/v1/me/profile-curation") suspend fun updateCuration(
        @Header("Authorization") authorization: String,
        @Body request: ProfileCurationUpdateRequest,
    ): RemoteProfile
    @PUT("api/v1/me/profile-privacy") suspend fun updateProfilePrivacy(
        @Header("Authorization") authorization: String,
        @Body request: ProfilePrivacyUpdateRequest,
    ): RemoteProfile
    @PUT("api/v1/me/melody-alias") suspend fun setMelodyAlias(
        @Header("Authorization") authorization: String,
        @Body request: MelodyAliasUpdateRequest,
    ): RemoteProfile
    @GET("api/v1/profiles/{profileHandle}") suspend fun publicProfile(
        @Header("Authorization") authorization: String,
        @Path("profileHandle") profileHandle: String,
    ): RemotePublicProfile
    @GET("api/v1/profiles/exchange/{exchangeId}") suspend fun exchangeProfile(
        @Header("Authorization") authorization: String,
        @Path("exchangeId") exchangeId: String,
    ): RemotePublicProfile
    @PUT("api/v1/me/music") suspend fun setMusic(
        @Header("Authorization") authorization: String,
        @Body request: ProfileMusicUpdateRequest,
    ): RemoteProfile
    @DELETE("api/v1/me/music") suspend fun deleteMusic(
        @Header("Authorization") authorization: String,
    ): RemoteProfile
}
