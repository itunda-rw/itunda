package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.Partner
import rw.itunda.core.domain.PartnerMiniApp
import rw.itunda.core.domain.PartnerMiniAppStatus

interface PartnerRepository : JpaRepository<Partner, String> {
    fun findByApiKeyHash(apiKeyHash: String): Partner?
    fun findByContactEmail(contactEmail: String): Partner?
}

interface PartnerMiniAppRepository : JpaRepository<PartnerMiniApp, String> {
    fun findByPartnerIdOrderByCreatedAtDesc(partnerId: String): List<PartnerMiniApp>
    fun findByStatusOrderByCreatedAtAsc(status: PartnerMiniAppStatus): List<PartnerMiniApp>
    fun findByStatus(status: PartnerMiniAppStatus): List<PartnerMiniApp>
}
