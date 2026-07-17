package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
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
    // Paginated -- see PageResponse.kt's doc comment for why (admin review queue and
    // published catalog can both grow past a safe single-response size).
    fun findByStatusOrderByCreatedAtAsc(status: PartnerMiniAppStatus, pageable: Pageable): Page<PartnerMiniApp>
    fun findByStatus(status: PartnerMiniAppStatus, pageable: Pageable): Page<PartnerMiniApp>
}
