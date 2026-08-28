package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.TransitRoute
import rw.itunda.core.domain.TransitScheduledTrip
import rw.itunda.core.domain.TransitStop
import rw.itunda.core.domain.TransitStopTime

// Real Kigali GTFS-backed schedule repositories -- see TransitStop.kt's own doc
// comment. All in one file since each is a plain JpaRepository with no real query
// logic of its own yet (TransitRoutingService does its own in-process nearest-stop/
// direct-route matching over findAll() -- a real Kigali stop count is small enough
// for this, same "keep it simple, this isn't a hot query path at this scale" reasoning
// closedWeekdays/goodPoints already established elsewhere this session).
interface TransitStopRepository : JpaRepository<TransitStop, String>
interface TransitRouteRepository : JpaRepository<TransitRoute, String>
interface TransitScheduledTripRepository : JpaRepository<TransitScheduledTrip, String>

interface TransitStopTimeRepository : JpaRepository<TransitStopTime, String> {
    fun findByStopIdIn(stopIds: Collection<String>): List<TransitStopTime>
}
