package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.RoundUpSettings

interface RoundUpSettingsRepository : JpaRepository<RoundUpSettings, String> {
    fun findByUserId(userId: String): RoundUpSettings?
}
