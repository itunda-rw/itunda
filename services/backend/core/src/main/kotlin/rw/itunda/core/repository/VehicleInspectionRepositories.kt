package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.VehicleInspectionBooking
import rw.itunda.core.domain.VehicleInspectionMechanic

interface VehicleInspectionMechanicRepository : JpaRepository<VehicleInspectionMechanic, String> {
    fun findByUserId(userId: String): VehicleInspectionMechanic?
    fun findByAvailableTrue(): List<VehicleInspectionMechanic>
}

interface VehicleInspectionBookingRepository : JpaRepository<VehicleInspectionBooking, String> {
    fun findByBuyerIdOrderByCreatedAtDesc(buyerId: String): List<VehicleInspectionBooking>
    fun findByMechanicIdOrderByCreatedAtDesc(mechanicId: String): List<VehicleInspectionBooking>
}
