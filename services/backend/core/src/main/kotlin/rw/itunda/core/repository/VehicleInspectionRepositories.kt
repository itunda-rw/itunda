package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.VehicleInspectionBooking
import rw.itunda.core.domain.VehicleInspectionMechanic
import rw.itunda.core.domain.VehicleInspectionStatus
import java.time.Instant

interface VehicleInspectionMechanicRepository : JpaRepository<VehicleInspectionMechanic, String> {
    fun findByUserId(userId: String): VehicleInspectionMechanic?
    fun findByAvailableTrue(): List<VehicleInspectionMechanic>
}

interface VehicleInspectionBookingRepository : JpaRepository<VehicleInspectionBooking, String> {
    fun findByBuyerIdOrderByCreatedAtDesc(buyerId: String): List<VehicleInspectionBooking>
    fun findByMechanicIdOrderByCreatedAtDesc(mechanicId: String): List<VehicleInspectionBooking>

    // Real no-show poll -- see VehicleInspectionNoShowScheduler's own doc comment.
    // Read-only candidate list, same "query outside any transaction, resolve each real
    // row inside its own per-item @Transactional method" shape
    // GiftVoucherExpiryScheduler/GiftVoucherService.expireVoucher already establish.
    fun findByStatusAndScheduledForBefore(status: VehicleInspectionStatus, cutoff: Instant): List<VehicleInspectionBooking>
}
