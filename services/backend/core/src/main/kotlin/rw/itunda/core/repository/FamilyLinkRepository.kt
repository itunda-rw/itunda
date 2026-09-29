package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.FamilyLink
import rw.itunda.core.domain.FamilyLinkStatus

interface FamilyLinkRepository : JpaRepository<FamilyLink, String> {
    fun findByIdAndGuardianUserId(id: String, guardianUserId: String): FamilyLink?
    fun findByIdAndChildUserId(id: String, childUserId: String): FamilyLink?
    fun findByGuardianUserIdAndStatus(guardianUserId: String, status: FamilyLinkStatus): List<FamilyLink>
    fun findByChildUserIdAndStatus(childUserId: String, status: FamilyLinkStatus): List<FamilyLink>
    fun findByGuardianUserIdAndChildUserIdAndStatusIn(guardianUserId: String, childUserId: String, statuses: List<FamilyLinkStatus>): List<FamilyLink>
    fun findByGuardianUserIdAndChildUserIdAndStatus(guardianUserId: String, childUserId: String, status: FamilyLinkStatus): FamilyLink?

    // Real spend-limit enforcement (2026-07-27) -- see FamilyLink.kt's own doc comment.
    // A child can have at most one ACTIVE guardian link with a real, enforced
    // dailySpendLimit at a time in this v1 (the first one found is the one enforced);
    // multiple guardians linking the same child with different limits is a genuine,
    // not-yet-modeled follow-up.
    fun findByChildUserIdAndStatusAndDailySpendLimitIsNotNull(childUserId: String, status: FamilyLinkStatus): FamilyLink?
}
