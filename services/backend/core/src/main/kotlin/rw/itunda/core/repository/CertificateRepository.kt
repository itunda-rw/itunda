package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.Certificate
import rw.itunda.core.domain.CertificateStatus

interface CertificateRepository : JpaRepository<Certificate, String> {
    fun findByUserIdAndStatus(userId: String, status: CertificateStatus): Certificate?
    fun findBySerialNumber(serialNumber: String): Certificate?
}
