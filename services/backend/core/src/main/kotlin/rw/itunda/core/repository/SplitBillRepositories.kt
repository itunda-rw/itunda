package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.SplitBill
import rw.itunda.core.domain.SplitBillParticipant

interface SplitBillRepository : JpaRepository<SplitBill, String> {
    fun findByGroupConversationIdOrderByCreatedAtDesc(groupConversationId: String): List<SplitBill>
}

interface SplitBillParticipantRepository : JpaRepository<SplitBillParticipant, String> {
    fun findBySplitBillId(splitBillId: String): List<SplitBillParticipant>
    fun findBySplitBillIdAndUserId(splitBillId: String, userId: String): SplitBillParticipant?
}
