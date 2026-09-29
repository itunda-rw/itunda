package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.LinkedAccount
import rw.itunda.core.domain.LinkedAccountStatus

interface LinkedAccountRepository : JpaRepository<LinkedAccount, String> {
    fun findByUserIdOrderByLinkedAtDesc(userId: String): List<LinkedAccount>
    fun countByStatus(status: LinkedAccountStatus): Long
}
