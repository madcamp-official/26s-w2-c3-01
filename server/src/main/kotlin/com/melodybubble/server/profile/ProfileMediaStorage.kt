package com.melodybubble.server.profile

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.CopyObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.ServerSideEncryption
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import java.time.Duration
import java.util.Base64
import java.util.UUID

data class StoredMedia(val key: String, val mimeType: String)

@Service
class ProfileMediaStorage(
    private val s3: S3Client,
    private val presigner: S3Presigner,
    @Value("\${app.media.bucket:}") private val bucket: String,
) {
    fun storeAvatar(userId: UUID, dataUrl: String): StoredMedia {
        val (mimeType, bytes) = decodeDataUrl(dataUrl, 512_000)
        require(mimeType in setOf("image/jpeg", "image/png", "image/webp")) { "Unsupported avatar format" }
        return put("users/$userId/avatar/${UUID.randomUUID()}.${extension(mimeType)}", mimeType, bytes)
    }

    fun storeCandidate(userId: UUID, audioBase64: String, mimeType: String): StoredMedia {
        require(mimeType in setOf("audio/mpeg", "audio/wav", "audio/x-wav", "audio/aac", "audio/mp4")) {
            "Unsupported audio format"
        }
        val bytes = Base64.getDecoder().decode(audioBase64)
        require(bytes.size <= 10_000_000) { "Generated audio is too large" }
        return put("users/$userId/candidates/${UUID.randomUUID()}.${extension(mimeType)}", mimeType, bytes, true)
    }

    fun signedUrl(key: String?): String? {
        if (key.isNullOrBlank() || bucket.isBlank()) return null
        val request = GetObjectRequest.builder().bucket(bucket).key(key).build()
        return presigner.presignGetObject(
            GetObjectPresignRequest.builder().signatureDuration(Duration.ofHours(1)).getObjectRequest(request).build(),
        ).url().toString()
    }

    fun promoteCandidate(userId: UUID, candidateKey: String): StoredMedia {
        val prefix = "users/$userId/candidates/"
        require(candidateKey.startsWith(prefix) && !candidateKey.removePrefix(prefix).contains('/')) {
            "Invalid music candidate"
        }
        check(bucket.isNotBlank()) { "MEDIA_BUCKET is not configured" }
        val metadata = s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(candidateKey).build())
        val mimeType = metadata.contentType().orEmpty()
        require(mimeType.startsWith("audio/")) { "Candidate is not audio" }
        val destination = "users/$userId/profile-music/${UUID.randomUUID()}.${extension(mimeType)}"
        s3.copyObject(
            CopyObjectRequest.builder().bucket(bucket).key(destination)
                .copySource("$bucket/$candidateKey").contentType(mimeType)
                .metadataDirective("REPLACE").serverSideEncryption(ServerSideEncryption.AES256).build(),
        )
        delete(candidateKey)
        return StoredMedia(destination, mimeType)
    }

    fun delete(key: String?) {
        if (key.isNullOrBlank() || bucket.isBlank()) return
        runCatching { s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build()) }
            .onFailure { if (it !is S3Exception || it.statusCode() != 404) throw it }
    }

    private fun put(key: String, mimeType: String, bytes: ByteArray, temporary: Boolean = false): StoredMedia {
        check(bucket.isNotBlank()) { "MEDIA_BUCKET is not configured" }
        val builder = PutObjectRequest.builder().bucket(bucket).key(key).contentType(mimeType)
            .serverSideEncryption(ServerSideEncryption.AES256)
        if (temporary) builder.tagging("temporary=true")
        val request = builder.build()
        s3.putObject(request, RequestBody.fromBytes(bytes))
        return StoredMedia(key, mimeType)
    }

    private fun decodeDataUrl(value: String, maxBytes: Int): Pair<String, ByteArray> {
        val match = Regex("^data:([^;]+);base64,(.+)$", RegexOption.DOT_MATCHES_ALL).matchEntire(value)
            ?: throw IllegalArgumentException("Invalid media data URL")
        val bytes = Base64.getDecoder().decode(match.groupValues[2])
        require(bytes.size <= maxBytes) { "Media is too large" }
        return match.groupValues[1].lowercase() to bytes
    }

    private fun extension(mimeType: String): String = when (mimeType) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "audio/wav", "audio/x-wav" -> "wav"
        "audio/aac" -> "aac"
        "audio/mp4" -> "m4a"
        else -> "mp3"
    }
}
