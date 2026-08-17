package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.CommunityNotificationPreference

interface CommunityNotificationPreferenceRepository : JpaRepository<CommunityNotificationPreference, String> {
    fun findByUserId(userId: String): CommunityNotificationPreference?
}
