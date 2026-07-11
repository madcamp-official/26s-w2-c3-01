package com.example.myapplication

import com.example.myapplication.core.model.Track
import com.example.myapplication.data.presence.DetectedPlaybackState
import com.example.myapplication.data.presence.toMusicUpdateRequest
import com.example.myapplication.data.reactionTypeForLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresenceSyncContractTest {
    @Test
    fun stoppedPlaybackClearsRemoteMusic() {
        val request = DetectedPlaybackState().toMusicUpdateRequest()

        assertEquals("", request.title)
        assertEquals("", request.artist)
        assertFalse(request.isPlaying)
    }

    @Test
    fun onlyDetectedPlaybackIsSerialized() {
        val request = DetectedPlaybackState(
            track = Track(
                id = "detected",
                title = "Night Drive",
                artist = "Melody",
                platform = "MEDIA_SESSION",
            ),
            isPlaying = true,
        ).toMusicUpdateRequest()

        assertEquals("Night Drive", request.title)
        assertEquals("Melody", request.artist)
        assertEquals("ANDROID_MEDIA_SESSION", request.sourceType)
        assertTrue(request.isPlaying)
    }

    @Test
    fun nonPlayingStateNeverLeaksStaleTrackMetadata() {
        val request = DetectedPlaybackState(
            track = Track(id = "stale", title = "Old", artist = "Track"),
            isPlaying = false,
        ).toMusicUpdateRequest()

        assertEquals("", request.title)
        assertEquals("", request.artist)
        assertFalse(request.isPlaying)
    }

    @Test
    fun playbackRestoredFromDiskMustBeReverifiedBeforeUpload() {
        val request = DetectedPlaybackState(
            track = Track(id = "restored", title = "Old song", artist = "Old artist"),
            isPlaying = true,
            verifiedInCurrentProcess = false,
        ).toMusicUpdateRequest()

        assertEquals("", request.title)
        assertEquals("", request.artist)
        assertFalse(request.isPlaying)
    }

    @Test
    fun safeReactionLabelsMapToServerEnums() {
        assertEquals("LIKE", reactionTypeForLabel("이 곡 좋아요"))
        assertEquals("SAME_TASTE", reactionTypeForLabel("취향이 닮았어요"))
        assertEquals("GREAT_PICK", reactionTypeForLabel("선곡 멋져요"))
        assertEquals("LISTEN_TOGETHER", reactionTypeForLabel("같이 듣고 싶어요"))
        assertNull(reactionTypeForLabel("사용자 입력"))
    }
}
