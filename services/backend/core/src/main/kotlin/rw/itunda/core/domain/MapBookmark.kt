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
 *
 * `folderName`/`color` added 2026-07-22 (migration V73) -- Naver/Kakao Maps' own real
 * "My Places" folder grouping, closing item 4 from the Maps design-doc sweep. Every
 * bookmark belongs to exactly one named folder with its own pin color; a bookmark made
 * before this existed defaults into a single real "Saved places" folder in the same
 * star-yellow (#F5A623) the star icon already used, not a placeholder value.
 *
 * `isPublic` added 2026-08-04 (migration V225) -- real Naver Map saved-place lists are
 * public/private per list with a shareable URL (echeveau.net/antennagom.com, cited in
 * DESIGN_REFERENCES.md's Maps section). itunda has no public web frontend for Maps to
 * host a browser-openable share URL on (only the existing app-only `itunda://maps` deep
 * link, see AndroidManifest.xml) -- rather than invent a public web surface this repo
 * has no other precedent for, sharing works the same real way the existing deep link
 * already does: a folder made public is readable via a real, unauthenticated
 * `GET /api/v1/maps/shared/{userId}/{folderName}` (see MapsService.getPublicFolder),
 * openable by anyone with the app via that same real deep-link mechanism. Defaults false
 * -- a bookmark is private unless its owner explicitly shares it, same as before this
 * field existed.
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

    @Column(name = "folder_name", nullable = false, length = 120)
    val folderName: String = "Saved places",

    @Column(nullable = false, length = 7)
    val color: String = "#F5A623",

    @Column(name = "is_public", nullable = false)
    val isPublic: Boolean = false,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", userId = "", displayName = "", latitude = 0.0, longitude = 0.0)
}
