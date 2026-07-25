package rw.itunda.notifications.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.domain.DevicePlatform
import rw.itunda.core.domain.DeviceToken
import rw.itunda.core.repository.DeviceTokenRepository
import rw.itunda.core.security.CurrentUser
import java.time.Instant
import java.util.UUID

data class RegisterDeviceTokenRequest(val platform: DevicePlatform, val token: String)

// Real push device-token registration -- see DeviceToken.kt's own doc comment. Not
// money-moving, no Idempotency-Key -- a real client-generated token is already its own
// natural idempotency key (the unique constraint on `token` makes a repeat register
// call a real no-op upsert, never a duplicate row).
@RestController
@RequestMapping("/api/v1/notifications/device-tokens")
class DeviceTokenController(
    private val deviceTokenRepository: DeviceTokenRepository,
) {
    @PostMapping
    fun register(
        @RequestBody request: RegisterDeviceTokenRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val trimmedToken = request.token.trim()
        val existing = deviceTokenRepository.findByToken(trimmedToken)
        val saved = if (existing != null) {
            // Real re-registration/device-handoff -- see DeviceToken.kt's own doc
            // comment on why this overwrites userId rather than rejecting or duplicating.
            existing.userId = currentUser.userId
            existing.updatedAt = Instant.now()
            deviceTokenRepository.save(existing)
        } else {
            deviceTokenRepository.save(
                DeviceToken(id = "device_token_${UUID.randomUUID()}", userId = currentUser.userId, platform = request.platform, token = trimmedToken),
            )
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "deviceToken" to saved))
    }

    // Real bug found live during this feature's own verification: Spring Data JPA's
    // derived deleteByToken performs a real remove() per matched row, which -- unlike
    // save()/findBy... -- requires an active transaction; without @Transactional here
    // it threw a raw 500 (jakarta.persistence.TransactionRequiredException) on every
    // real unregister call.
    @Transactional
    @DeleteMapping("/{token}")
    fun unregister(@PathVariable token: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val existing = deviceTokenRepository.findByToken(token)
        if (existing != null && existing.userId == currentUser.userId) {
            deviceTokenRepository.deleteByToken(token)
        }
        return ResponseEntity.ok(mapOf("success" to true))
    }
}
