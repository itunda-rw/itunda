package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.GroupEatsOrder
import rw.itunda.core.domain.GroupEatsOrderItem
import rw.itunda.core.domain.GroupEatsOrderParticipant

interface GroupEatsOrderRepository : JpaRepository<GroupEatsOrder, String> {
    fun findByJoinCode(joinCode: String): GroupEatsOrder?
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
