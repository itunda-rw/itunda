package rw.itunda.core.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.GroupEatsOrder
import rw.itunda.core.domain.GroupEatsOrderItem
import rw.itunda.core.domain.GroupEatsOrderParticipant
import java.util.Optional

interface GroupEatsOrderRepository : JpaRepository<GroupEatsOrder, String> {
    fun findByJoinCode(joinCode: String): GroupEatsOrder?

    // Real fix for a genuine double-finalize/double-cancel race (found via a fresh
    // concurrency audit, 2026-08-16): finalizeOrder/cancel both used to read-check-then-
    // write groupOrder.status with no lock, so two concurrent finalize calls for the
    // same group order (a real double-tap, or two devices) could both read OPEN, both
    // pass the check, and both call EatsOrderService.placeOrder + createDirectSplitBill
    // -- two real orders, two real ledger charges, duplicate split-bill requests --
    // before either committed FINALIZED. Same findByIdForUpdate convention
    // AccountRepository/FraudFlagRepository/DebitCardRepository already establish for
    // this exact class of bug.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GroupEatsOrder g where g.id = :id")
    fun findByIdForUpdate(@Param("id") id: String): Optional<GroupEatsOrder>
}

interface GroupEatsOrderParticipantRepository : JpaRepository<GroupEatsOrderParticipant, String> {
    fun findByGroupOrderId(groupOrderId: String): List<GroupEatsOrderParticipant>
    fun findByGroupOrderIdAndUserId(groupOrderId: String, userId: String): GroupEatsOrderParticipant?
}

interface GroupEatsOrderItemRepository : JpaRepository<GroupEatsOrderItem, String> {
    fun findByGroupOrderId(groupOrderId: String): List<GroupEatsOrderItem>
    fun findByGroupOrderIdAndUserId(groupOrderId: String, userId: String): List<GroupEatsOrderItem>
    fun deleteByGroupOrderIdAndUserId(groupOrderId: String, userId: String)
}
