package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.BillAutoPaySetting

interface BillAutoPaySettingRepository : JpaRepository<BillAutoPaySetting, String> {
    fun findByUserId(userId: String): List<BillAutoPaySetting>
    fun findByUserIdAndProviderId(userId: String, providerId: String): BillAutoPaySetting?
    fun findByActiveTrue(): List<BillAutoPaySetting>
}
