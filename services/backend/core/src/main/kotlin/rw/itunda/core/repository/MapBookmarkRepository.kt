package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.MapBookmark

interface MapBookmarkRepository : JpaRepository<MapBookmark, String> {
    fun findByUserIdAndLatitudeAndLongitude(userId: String, latitude: Double, longitude: Double): MapBookmark?

    fun findByUserIdOrderByCreatedAtDesc(userId: String): List<MapBookmark>

    fun deleteByUserIdAndLatitudeAndLongitude(userId: String, latitude: Double, longitude: Double): Long
}
