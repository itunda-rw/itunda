package rw.itunda.core.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.GiftVoucher
import rw.itunda.core.domain.GiftVoucherStatus
import java.time.Instant
import java.util.Optional

interface GiftVoucherRepository : JpaRepository<GiftVoucher, String> {
    /** Redemption is a state transition on real money; only one caller may claim a voucher. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from GiftVoucher v where v.id = :id")
    fun findByIdForUpdate(@Param("id") id: String): Optional<GiftVoucher>

    fun findByStatusAndExpiresAtBefore(status: GiftVoucherStatus, expiresAt: Instant): List<GiftVoucher>
    fun findByConversationId(conversationId: String): List<GiftVoucher>

    // Real expiry-reminder sweep -- see GiftVoucher.expiryReminderSentAt's own doc
    // comment. Every real ACTIVE voucher that hasn't been reminded yet; the scheduler
    // filters this down to vouchers whose expiresAt has actually entered the real
    // 7-day reminder window, same shape
    // InsurancePolicyRepository/CertificateRepository's own equivalent methods
    // establish.
    fun findByStatusAndExpiryReminderSentAtIsNull(status: GiftVoucherStatus): List<GiftVoucher>
}
