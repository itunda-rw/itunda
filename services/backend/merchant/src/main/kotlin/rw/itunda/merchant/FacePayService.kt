package rw.itunda.merchant

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.FacePayEnrollment
import rw.itunda.core.repository.FacePayEnrollmentRepository
import java.time.Instant
import java.util.UUID

class FacePayNotEnrolledException(message: String) : RuntimeException(message)

/**
 * Real Face Pay -- see FacePayEnrollment's own doc comment for the full account of
 * why this stores no biometric data. Composes MerchantService rather than
 * duplicating its collection logic: a Face Pay checkout is the exact same
 * PaymentIntent/ledger/fee/fraud flow as a QR collection, differing only in which
 * authentication factor identified and authorized the payer -- a merchant still
 * generates a fixed-amount intent the same way (POST /merchant/qr/generate; itunda
 * doesn't distinguish "a QR code" from "an amount a terminal is asking to charge" at
 * the data-model level, matching how Toss's own real FacePay works at participating
 * merchants: the terminal states an amount, the customer's face authorizes it).
 */
@Service
class FacePayService(
    private val facePayEnrollmentRepository: FacePayEnrollmentRepository,
    private val merchantService: MerchantService,
) {
    @Transactional
    fun enroll(userId: String): FacePayEnrollment {
        val existing = facePayEnrollmentRepository.findByUserId(userId)
        if (existing != null) {
            existing.active = true
            existing.enrolledAt = Instant.now()
            existing.revokedAt = null
            return facePayEnrollmentRepository.save(existing)
        }
        return facePayEnrollmentRepository.save(
            FacePayEnrollment(id = "facepay_${UUID.randomUUID()}", userId = userId),
        )
    }

    @Transactional
    fun revoke(userId: String): FacePayEnrollment {
        val enrollment = facePayEnrollmentRepository.findByUserId(userId)
            ?: throw FacePayNotEnrolledException("This account is not enrolled in Face Pay")
        enrollment.active = false
        enrollment.revokedAt = Instant.now()
        return facePayEnrollmentRepository.save(enrollment)
    }

    fun status(userId: String): FacePayEnrollment? =
        facePayEnrollmentRepository.findByUserId(userId)?.takeIf { it.active }

    @Transactional
    fun collect(payerUserId: String, intentId: String): Map<String, Any?> {
        val enrollment = facePayEnrollmentRepository.findByUserId(payerUserId)
        if (enrollment == null || !enrollment.active) {
            throw FacePayNotEnrolledException("Enroll in Face Pay before paying with it")
        }
        return merchantService.collect(payerUserId, intentId, channel = "FACE_PAY")
    }
}
