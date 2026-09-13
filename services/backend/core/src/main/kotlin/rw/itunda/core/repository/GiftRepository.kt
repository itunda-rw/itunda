package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.Gift
import rw.itunda.core.domain.GiftStatus
import java.time.Instant

interface GiftRepository : JpaRepository<Gift, String> {
    fun findByStatusAndExpiresAtBefore(status: GiftStatus, expiresAt: Instant): List<Gift>
    fun findByConversationId(conversationId: String): List<Gift>
    fun findByStatusAndExpiryReminderSentAtIsNull(status: GiftStatus): List<Gift>
}
