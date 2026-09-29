package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * A real Kigali public-transit stop (itunda Maps redesign, 2026-08-28, direct Naver Map
 * reference: the directions panel's Transit mode). Sourced from the real, validated
 * Kigali GTFS dataset (TUMI Datahub / Digital Transport for Africa initiative) --
 * `id`/`name`/`latitude`/`longitude` map directly onto that real feed's own
 * `stop_id`/`stop_name`/`stop_lat`/`stop_lon` columns, imported once via
 * `TransitGtfsImportService` (a genuine GTFS-zip parser, not hand-entered sample data).
 * Explicitly separate from `transit`'s own `TransitBalance`/fare-card domain (a
 * stored-value payment product with zero location data) and `rideshare`'s `BusService`
 * (intercity coach ticket sales, also zero stop-level data) -- neither of those had any
 * real geo/stop/route model this could have reused.
 */
@Entity
@Table(name = "transit_stops")
class TransitStop(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(nullable = false, length = 200)
    val name: String,

    @Column(nullable = false)
    val latitude: Double,

    @Column(nullable = false)
    val longitude: Double,
) {
    protected constructor() : this(id = "", name = "", latitude = 0.0, longitude = 0.0)
}
