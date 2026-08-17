package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.EatsMembership

interface EatsMembershipRepository : JpaRepository<EatsMembership, String> {
    fun findByUserId(userId: String): EatsMembership?
    fun findByReminderSentAtIsNull(): List<EatsMembership>
}
