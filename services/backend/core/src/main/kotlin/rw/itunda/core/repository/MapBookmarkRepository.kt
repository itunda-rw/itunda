package rw.itunda.core.repository

import java.time.Instant
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.MapBookmark

interface MapBookmarkRepository : JpaRepository<MapBookmark, String> {
    fun findByUserIdAndLatitudeAndLongitude(userId: String, latitude: Double, longitude: Double): MapBookmark?

    fun findByUserIdOrderByCreatedAtDesc(userId: String): List<MapBookmark>

    fun deleteByUserIdAndLatitudeAndLongitude(userId: String, latitude: Double, longitude: Double): Long

    // Real shareable public/private folder -- see MapBookmark.isPublic's own doc comment.
    fun findByUserIdAndFolderName(userId: String, folderName: String): List<MapBookmark>

    fun findByUserIdAndFolderNameAndIsPublicTrueOrderByCreatedAtDesc(userId: String, folderName: String): List<MapBookmark>

    // Real cross-user "popular this week" aggregate -- see MapsService.getTrendingSavedPlaces's
    // own doc comment. COUNT(DISTINCT userId) so one user re-saving the same place (not
    // actually possible today given addBookmark's own unique constraint, but a real defensive
    // choice regardless) never inflates the real count.
    @Query(
        "SELECT b.displayName AS displayName, b.latitude AS latitude, b.longitude AS longitude, " +
            "COUNT(DISTINCT b.userId) AS saveCount " +
            "FROM MapBookmark b WHERE b.createdAt >= :since " +
            "GROUP BY b.displayName, b.latitude, b.longitude " +
            "ORDER BY saveCount DESC",
    )
    fun findTrending(@Param("since") since: Instant, pageable: Pageable): List<TrendingBookmarkProjection>
}

interface TrendingBookmarkProjection {
    fun getDisplayName(): String
    fun getLatitude(): Double
    fun getLongitude(): Double
    fun getSaveCount(): Long
}
