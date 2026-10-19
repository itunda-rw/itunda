package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.PartnerMiniAppRelease
import rw.itunda.core.domain.PartnerMiniAppReleaseStatus

interface PartnerMiniAppReleaseRepository : JpaRepository<PartnerMiniAppRelease, String> {
    fun findByMiniAppIdOrderByCreatedAtDesc(miniAppId: String): List<PartnerMiniAppRelease>
    fun findByMiniAppIdAndStatus(miniAppId: String, status: PartnerMiniAppReleaseStatus): List<PartnerMiniAppRelease>
}
