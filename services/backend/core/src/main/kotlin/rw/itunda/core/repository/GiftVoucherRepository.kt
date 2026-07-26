package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.GiftVoucher
import rw.itunda.core.domain.GiftVoucherStatus
import java.time.Instant

interface GiftVoucherRepository : JpaRepository<GiftVoucher, String> {
    fun findByStatusAndExpiresAtBefore(status: GiftVoucherStatus, expiresAt: Instant): List<GiftVoucher>
    fun findByConversationId(conversationId: String): List<GiftVoucher>
}
