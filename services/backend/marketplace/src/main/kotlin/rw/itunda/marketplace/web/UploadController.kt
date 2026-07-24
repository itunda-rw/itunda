package rw.itunda.marketplace.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import rw.itunda.auth.RateLimiter
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import java.io.File
import java.time.Duration
import java.util.UUID

class InvalidUploadException(message: String) : RuntimeException(message)

/**
 * Real photo upload (2026-07-24) -- closes the gap this session's own repo-wide audit
 * found before adding Listing.photoUrl: every "photo"/"image" field in this backend
 * (Merchant.photoUrl, MerchantProduct.imageUrl, User.profilePhotoUrl) was a
 * paste-your-own-externally-hosted-URL string, with genuinely no upload/storage layer
 * anywhere in the repo. That's an honest v1 scope for a merchant who already has a
 * hosted storefront photo, but not for an ordinary Hood seller photographing a
 * secondhand item on their phone -- "paste a URL" isn't a real action most people can
 * take. This is real, minimal file storage: local disk on itunda-dc-a (the only node
 * this cluster has), served straight back out under the same `/api/v1` path so it rides
 * the existing gateway/tunnel routing with zero new proxy config.
 *
 * Deliberately not S3/MinIO/a CDN -- this cluster is already at its real hardware
 * ceiling (see docs/PRIVATE_CLOUD_BLUEPRINT.md and this session's own live account of
 * it); a plain validated-and-bounded local directory is the honest, lean choice for a
 * single-node deployment, matching go-pmtiles' own "serve real files, no heavier than
 * it needs to be" precedent from the same session.
 */
@RestController
@RequestMapping("/api/v1")
class UploadController(
    private val rateLimiter: RateLimiter,
) {
    private val uploadDir = File(System.getenv("UPLOAD_DIR") ?: "/uploads")

    companion object {
        private const val MAX_FILE_BYTES = 5L * 1024 * 1024
        private val ALLOWED_TYPES = mapOf(
            "image/jpeg" to "jpg",
            "image/png" to "png",
            "image/webp" to "webp",
        )
    }

    @PostMapping("/uploads")
    fun upload(
        @RequestParam("file") file: MultipartFile,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        // Same anti-abuse discipline as every other user-content-creation endpoint
        // this session's own security review established (marketplace listings,
        // Partner SDK, Certificate) -- an upload endpoint with no rate limit is a
        // real disk-fill vector.
        rateLimiter.checkLimit("uploads:${currentUser.userId}", limit = 20, window = Duration.ofHours(1))

        if (file.isEmpty) {
            throw InvalidUploadException("No file provided")
        }
        if (file.size > MAX_FILE_BYTES) {
            throw InvalidUploadException("File must be 5MB or smaller")
        }
        val extension = ALLOWED_TYPES[file.contentType]
            ?: throw InvalidUploadException("Only JPEG, PNG, or WebP images are supported")

        uploadDir.mkdirs()
        val filename = "${UUID.randomUUID()}.$extension"
        file.transferTo(File(uploadDir, filename))

        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "url" to "/api/v1/uploads/$filename"))
    }

    @ExceptionHandler(InvalidUploadException::class)
    fun handleInvalid(ex: InvalidUploadException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_UPLOAD", ex.message ?: "Bad request"))
}
