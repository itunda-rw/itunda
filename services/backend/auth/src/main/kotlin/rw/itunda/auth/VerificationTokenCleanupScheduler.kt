package rw.itunda.auth

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.repository.EmailVerificationTokenRepository
import rw.itunda.core.repository.PhoneVerificationTokenRepository
import java.time.Instant

/**
 * Retains verification challenges only while they may be accepted. This is deliberately
 * separate from validation: expiry remains enforced synchronously by [AuthService], so
 * a delayed scheduler can never revive a challenge.
 */
@Component
class VerificationTokenCleanupScheduler(
    private val emailVerificationTokenRepository: EmailVerificationTokenRepository,
    private val phoneVerificationTokenRepository: PhoneVerificationTokenRepository,
) {
    private val log = LoggerFactory.getLogger(VerificationTokenCleanupScheduler::class.java)

    @Scheduled(fixedDelayString = "\${itunda.auth.verification-token-cleanup-interval-ms:3600000}")
    @Transactional
    fun run() {
        val now = Instant.now()
        val emailDeleted = emailVerificationTokenRepository.deleteExpiredBefore(now)
        val phoneDeleted = phoneVerificationTokenRepository.deleteExpiredBefore(now)
        if (emailDeleted + phoneDeleted > 0) {
            log.info("Removed {} expired email and {} expired phone verification challenge(s)", emailDeleted, phoneDeleted)
        }
    }
}
