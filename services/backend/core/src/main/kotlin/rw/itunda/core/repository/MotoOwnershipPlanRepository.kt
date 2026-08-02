package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.MotoOwnershipPlan
import rw.itunda.core.domain.MotoOwnershipPlanStatus

interface MotoOwnershipPlanRepository : JpaRepository<MotoOwnershipPlan, String> {
    fun findByUserId(userId: String): List<MotoOwnershipPlan>

    // "One active plan at a time" eligibility check -- active means SAVING or
    // LOAN_ACTIVE, not COMPLETED/CANCELLED.
    fun findByUserIdAndStatusIn(userId: String, statuses: List<MotoOwnershipPlanStatus>): List<MotoOwnershipPlan>
}
