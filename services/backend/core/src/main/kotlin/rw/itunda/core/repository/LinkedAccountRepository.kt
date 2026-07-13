package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.LinkedAccount

interface LinkedAccountRepository : JpaRepository<LinkedAccount, String> {
    fun findByUserIdOrderByLinkedAtDesc(userId: String): List<LinkedAccount>
}
