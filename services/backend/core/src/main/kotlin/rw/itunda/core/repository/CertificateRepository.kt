package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.Certificate
import rw.itunda.core.domain.CertificateStatus

interface CertificateRepository : JpaRepository<Certificate, String> {
    fun findByUserIdAndStatus(userId: String, status: CertificateStatus): Certificate?
    fun findBySerialNumber(serialNumber: String): Certificate?

    // Real renewal-reminder sweep -- see Certificate.renewalReminderSentAt's own doc
    // comment. Every real ACTIVE certificate that hasn't been reminded yet; the
    // scheduler filters this down to certificates whose expiresAt has actually entered
    // the real 60-day renewal window, same shape
    // InsurancePolicyRepository.findByStatusAndRenewalReminderSentAtIsNull establishes.
    fun findByStatusAndRenewalReminderSentAtIsNull(status: CertificateStatus): List<Certificate>
}
