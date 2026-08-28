package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/** A real Kigali public-transit route -- see TransitStop.kt's own doc comment. Maps
 * directly onto the real GTFS feed's `routes.txt` (route_short_name/route_long_name). */
@Entity
@Table(name = "transit_routes")
class TransitRoute(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "short_name", length = 32)
    val shortName: String?,

    @Column(name = "long_name", length = 200)
    val longName: String?,
) {
    protected constructor() : this(id = "", shortName = null, longName = null)
}
