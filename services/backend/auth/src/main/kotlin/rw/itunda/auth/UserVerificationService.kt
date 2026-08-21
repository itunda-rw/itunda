package rw.itunda.auth

import org.slf4j.LoggerFactory
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import rw.itunda.core.domain.EmailVerificationToken
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.PhoneVerificationToken
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.EmailVerificationTokenRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.PhoneVerificationTokenRepository
import rw.itunda.core.repository.UserRepository
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Split out of AuthService.kt (2026-08-21) -- see
 * docs/ARCHITECTURE_GUIDELINES.md §2 and scripts/file-size-lint.py's own real
 * 500-line guideline, which AuthService.kt was 16 lines over. Email/phone OTP
 * verification is a real, cohesive concern distinct from AuthService's own core
 * registration/login/session responsibility -- AuthController is the only caller of
 * either half, so the split needed no cross-module changes. `sendPhoneVerificationCode`
 * lives here (not AuthService) even though `AuthService.register` also calls it, since
 * "generate and deliver a phone OTP" is squarely this class's own real concern, not
 * registration's -- `AuthService` injects this class as a collaborator instead.
 */
@Service
class UserVerificationService(
    private val userRepository: UserRepository,
    private val emailVerificationTokenRepository: EmailVerificationTokenRepository,
    private val phoneVerificationTokenRepository: PhoneVerificationTokenRepository,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
    private val rateLimiter: RateLimiter,
) {
    private val passwordEncoder = BCryptPasswordEncoder()
    private val logger = LoggerFactory.getLogger(UserVerificationService::class.java)
    private val secureRandom = SecureRandom()

    // Real, single-use, 30-minute token -- see EmailVerificationToken's doc comment.
    // Delivered via a real Notification (this backend's own existing in-app delivery
    // mechanism, already used for budget alerts) rather than a real email, since there
    // is no SMTP relay anywhere in this backend -- the token itself is real and never
    // echoed back in this endpoint's own response, so a client can't self-verify without
    // actually receiving it through that real channel.
    @Transactional
    fun requestEmailVerification(userId: String) {
        rateLimiter.checkLimit("auth:email-verify-request:$userId", limit = 3, window = Duration.ofMinutes(15))
        val user = userRepository.findById(userId).orElseThrow { UserNotFoundException("User not found") }
        if (user.email == null) throw NoEmailOnFileException("No email address on file to verify")
        if (user.emailVerified) throw EmailAlreadyVerifiedException("Email is already verified")

        val token = UUID.randomUUID().toString().replace("-", "")
        emailVerificationTokenRepository.invalidateUnusedByUserId(userId)
        emailVerificationTokenRepository.save(
            EmailVerificationToken(
                id = "evt_${UUID.randomUUID()}", userId = userId, token = passwordEncoder.encode(token),
                expiresAt = Instant.now().plusSeconds(1800), createdAt = Instant.now(),
            ),
        )
        val title = "Verify your email"
        val body = "Your email verification code is $token. It expires in 30 minutes."
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = userId, type = "PROFILE_EMAIL_VERIFICATION",
                title = title, body = body,
                isRead = false, createdAt = Instant.now(), dataJson = null,
            ),
        )
        // Real push (item 122, 2026-07-29) -- of every real Notification call site in
        // this backend, an OTP code delivery is the single most time-sensitive: a user
        // is actively waiting on this screen for it, unlike most other notification
        // types that are fine to pick up on the next in-app poll. See
        // PushNotificationService's own doc comment for the fuller "why these sites"
        // account.
        sendVerificationPushAfterCommit(userId, title, body)
    }

    @Transactional
    fun confirmEmailVerification(userId: String, token: String): PublicUser {
        val record = emailVerificationTokenRepository.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(userId)
            ?.takeIf { it.userId == userId }
            ?: throw InvalidVerificationTokenException("Invalid or expired verification token")
        if (record.usedAt != null || !passwordEncoder.matches(token, record.token) || record.expiresAt.isBefore(Instant.now())) {
            throw InvalidVerificationTokenException("Invalid or expired verification token")
        }
        record.usedAt = Instant.now()
        emailVerificationTokenRepository.save(record)

        val user = userRepository.findById(userId).orElseThrow { UserNotFoundException("User not found") }
        user.emailVerified = true
        userRepository.save(user)
        return user.toPublic()
    }

    /**
     * Real phone verification -- closes docs/DESIGN_REFERENCES.md's own recommendation
     * ("the single most Toss-aligned, currently-real gap: Toss's whole real-name-
     * verification model is built on proven phone possession; itunda currently accepts
     * any unverified number"). Mirrors `requestEmailVerification`/
     * `confirmEmailVerification` field-for-field, per that same recommendation's own
     * instruction, with two honest differences: the code is a real 6-digit OTP (matching
     * real phone-verification UX, not a hex token) and it's sent automatically at
     * registration itself (`AuthService.register` calls this directly), not only opt-in
     * from a profile screen -- "at registration" is the whole point of this gap.
     * Delivered via itunda's own real in-app Notification, same "no SMTP/SMS relay in
     * this backend, so reuse the real mechanism that does exist rather than fabricate
     * one" convention email verification already established -- honestly, this does NOT
     * prove real SMS possession the way a genuine carrier-delivered OTP would, since the
     * user is already authenticated when they see the in-app notification.
     */
    fun sendPhoneVerificationCode(userId: String) {
        // Authentication codes are secrets: SecureRandom avoids the predictability of
        // Math.random while preserving the existing six-digit UX and 30-minute expiry.
        val code = (100000 + secureRandom.nextInt(900000)).toString()
        // A resend replaces, rather than adds to, the active challenge set.
        phoneVerificationTokenRepository.invalidateUnusedByUserId(userId)
        phoneVerificationTokenRepository.save(
            PhoneVerificationToken(
                id = "pvt_${UUID.randomUUID()}", userId = userId, token = passwordEncoder.encode(code),
                expiresAt = Instant.now().plusSeconds(1800), createdAt = Instant.now(),
            ),
        )
        val title = "Verify your phone number"
        val body = "Your phone verification code is $code. It expires in 30 minutes."
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = userId, type = "PHONE_VERIFICATION",
                title = title, body = body,
                isRead = false, createdAt = Instant.now(), dataJson = null,
            ),
        )
        // Real push (item 122) -- see requestEmailVerification's own doc comment above.
        sendVerificationPushAfterCommit(userId, title, body)
    }

    /** OTP pushes are external effects; only expose a code once its token row has committed. */
    private fun sendVerificationPushAfterCommit(userId: String, title: String, body: String) {
        val send = {
            try {
                pushNotificationService.sendToUser(userId, title, body)
            } catch (e: Exception) {
                logger.warn("Could not send verification push for user {}", userId, e)
            }
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = send()
        })
    }

    /** Real resend, for the code sent automatically at registration expiring or getting lost. */
    @Transactional
    fun requestPhoneVerification(userId: String) {
        rateLimiter.checkLimit("auth:phone-verify-request:$userId", limit = 3, window = Duration.ofMinutes(15))
        val user = userRepository.findById(userId).orElseThrow { UserNotFoundException("User not found") }
        if (user.phoneVerified) throw PhoneAlreadyVerifiedException("Phone number is already verified")
        sendPhoneVerificationCode(userId)
    }

    @Transactional
    fun confirmPhoneVerification(userId: String, code: String): PublicUser {
        // Six-digit OTPs have a deliberately small keyspace for usability, so limit
        // guesses independently of resend limits before looking up the token.
        rateLimiter.checkLimit("auth:phone-verify:$userId", limit = 5, window = Duration.ofMinutes(15))
        val record = phoneVerificationTokenRepository.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(userId)
            ?.takeIf { it.userId == userId }
            ?: throw InvalidVerificationTokenException("Invalid or expired verification code")
        if (!passwordEncoder.matches(code, record.token) || record.expiresAt.isBefore(Instant.now())) {
            throw InvalidVerificationTokenException("Invalid or expired verification code")
        }
        record.usedAt = Instant.now()
        phoneVerificationTokenRepository.save(record)

        val user = userRepository.findById(userId).orElseThrow { UserNotFoundException("User not found") }
        user.phoneVerified = true
        userRepository.save(user)
        return user.toPublic()
    }
}
