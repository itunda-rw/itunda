package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.PlatformMembership

interface PlatformMembershipRepository : JpaRepository<PlatformMembership, String> {
    fun findByUserId(userId: String): PlatformMembership?
}
