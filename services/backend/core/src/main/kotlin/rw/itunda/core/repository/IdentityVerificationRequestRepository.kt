package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.IdentityVerificationRequest

interface IdentityVerificationRequestRepository : JpaRepository<IdentityVerificationRequest, String> {
    fun findByIdAndPartnerId(id: String, partnerId: String): IdentityVerificationRequest?
}
