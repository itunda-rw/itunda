package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.SplitBill
import rw.itunda.core.domain.SplitBillParticipant
import rw.itunda.core.domain.SplitBillStatus

interface SplitBillRepository : JpaRepository<SplitBill, String> {
    fun findByGroupConversationIdOrderByCreatedAtDesc(groupConversationId: String): List<SplitBill>

    // Real scheduled reminder nudges (2026-07-27) -- see SplitBillReminderScheduler's
    // own doc comment. Coarse repo filter (every real OPEN bill), exact due-or-not
    // logic in the service, same discipline this codebase's other scheduled sweeps
    // (AutoSaveScheduler, AutoTopUpScheduler) already use.
    fun findByStatus(status: SplitBillStatus): List<SplitBill>
}

interface SplitBillParticipantRepository : JpaRepository<SplitBillParticipant, String> {
    fun findBySplitBillId(splitBillId: String): List<SplitBillParticipant>
    fun findBySplitBillIdAndUserId(splitBillId: String, userId: String): SplitBillParticipant?
}
