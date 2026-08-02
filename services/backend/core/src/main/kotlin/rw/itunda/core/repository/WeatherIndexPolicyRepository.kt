package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.WeatherIndexPolicy
import rw.itunda.core.domain.WeatherIndexPolicyStatus

interface WeatherIndexPolicyRepository : JpaRepository<WeatherIndexPolicy, String> {
    fun findByUserId(userId: String): List<WeatherIndexPolicy>

    // The real batch-payout-evaluation query: every ENROLLED policy in a district+season
    // is a candidate the instant that season's rainfall index publishes.
    fun findByDistrictAndSeasonAndStatus(district: String, season: String, status: WeatherIndexPolicyStatus): List<WeatherIndexPolicy>
}
