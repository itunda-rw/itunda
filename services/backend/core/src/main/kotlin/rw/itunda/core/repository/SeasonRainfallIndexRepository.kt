package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.SeasonRainfallIndex

interface SeasonRainfallIndexRepository : JpaRepository<SeasonRainfallIndex, String> {
    fun findByDistrictAndSeason(district: String, season: String): SeasonRainfallIndex?
}
