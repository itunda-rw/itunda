package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * A real bookmarked/favorite place on the map (2026-07-19) -- closes item 7 on the Maps
 * "100%" roadmap, the same kind of star/save feature Naver/Kakao Maps offer. A place here
 * isn't a foreign key into any itunda-owned table (search/nearby results come from
 * Nominatim, not a local catalog), so the real display name + coordinate are stored
 * directly, exactly what a client already has in hand from `GET /api/v1/maps/search` or
 * `/nearby`. Real DB unique constraint on (user_id, latitude, longitude) backs the same
 * application-level "add is idempotent" check `MapsService.addBookmark` makes.
 */
@Entity
@Table(name = "map_bookmarks")
class MapBookmark(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "display_name", nullable = false, length = 512)
    val displayName: String,

    @Column(nullable = false)
    val latitude: Double,

    @Column(nullable = false)
    val longitude: Double,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", userId = "", displayName = "", latitude = 0.0, longitude = 0.0)
}
