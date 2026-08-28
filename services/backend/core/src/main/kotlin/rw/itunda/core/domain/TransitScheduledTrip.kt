package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * A real single scheduled run of a Kigali public-transit route -- see TransitStop.kt's
 * own doc comment. Maps onto the real GTFS feed's `trips.txt` (route_id/service_id).
 * Named `TransitScheduledTrip`, not `TransitTrip`, because `TransitTrip` already exists
 * in this same package (TransitBalance.kt) for a completely different real concept --
 * one real tap-to-pay fare record on itunda's own stored-value transit card. Confirmed
 * via a repo-wide grep before naming this, not assumed.
 */
@Entity
@Table(name = "transit_scheduled_trips")
class TransitScheduledTrip(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "route_id", nullable = false, length = 64)
    val routeId: String,

    // Real GTFS service_id (which real calendar/day pattern this trip runs on) --
    // stored for a future real day-of-week filter, not yet cross-referenced by
    // TransitRoutingService's own v1 (direct-route-only, no calendar filtering yet,
    // an honest, explicitly-scoped-down limitation -- see that class's own doc comment).
    @Column(name = "service_id", length = 64)
    val serviceId: String?,
) {
    protected constructor() : this(id = "", routeId = "", serviceId = null)
}
