package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.ScheduledTransfer
import rw.itunda.core.domain.ScheduledTransferStatus
import java.time.LocalDate

interface ScheduledTransferRepository : JpaRepository<ScheduledTransfer, String> {
    fun findByUserIdOrderByCreatedAtDesc(userId: String): List<ScheduledTransfer>
    fun findByIdAndUserId(id: String, userId: String): ScheduledTransfer?
    fun findByStatusAndScheduledDateLessThanEqual(status: ScheduledTransferStatus, scheduledDate: LocalDate): List<ScheduledTransfer>
}
