package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.GiftVoucher
import rw.itunda.core.domain.GiftVoucherStatus
import java.time.Instant

interface GiftVoucherRepository : JpaRepository<GiftVoucher, String> {
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
