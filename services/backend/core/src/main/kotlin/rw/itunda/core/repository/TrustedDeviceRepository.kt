package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.TrustedDevice

interface TrustedDeviceRepository : JpaRepository<TrustedDevice, String> {
    fun findByUserIdAndDeviceId(userId: String, deviceId: String): TrustedDevice?

    fun findByUserIdOrderByLastSeenAtDesc(userId: String): List<TrustedDevice>
}
