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
}
