package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.DeviceToken

interface DeviceTokenRepository : JpaRepository<DeviceToken, String> {
    fun findByUserId(userId: String): List<DeviceToken>
    fun findByToken(token: String): DeviceToken?
    fun deleteByToken(token: String)
}
