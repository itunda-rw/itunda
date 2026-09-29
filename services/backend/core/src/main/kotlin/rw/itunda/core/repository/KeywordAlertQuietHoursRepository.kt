package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.KeywordAlertQuietHours

interface KeywordAlertQuietHoursRepository : JpaRepository<KeywordAlertQuietHours, String> {
    fun findByUserId(userId: String): KeywordAlertQuietHours?
    fun findByUserIdIn(userIds: Collection<String>): List<KeywordAlertQuietHours>
}
