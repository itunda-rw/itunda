package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * One real scheduled stop within a real TransitScheduledTrip -- see TransitStop.kt's
 * own doc comment. Maps onto the real GTFS feed's `stop_times.txt`
 * (trip_id/stop_id/arrival_time/departure_time/stop_sequence).
 *
 * `arrivalSecondsAfterMidnight`/`departureSecondsAfterMidnight` are real GTFS
 * "HH:MM:SS" times converted to a plain seconds-since-midnight int at import time
 * (`TransitGtfsImportService.parseGtfsTime`) -- GTFS deliberately allows values past
 * 24:00:00 for a trip that runs past midnight (e.g. "25:30:00"), which this
 * representation handles naturally (91800) where a real `java.time.LocalTime` cannot
 * (it would throw on an out-of-range hour). Real schedule-based, not live-GPS-tracked
 * -- see TransitRoutingService's own doc comment for the honest distinction.
 */
@Entity
@Table(name = "transit_stop_times")
class TransitStopTime(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "trip_id", nullable = false, length = 64)
    val tripId: String,

    @Column(name = "stop_id", nullable = false, length = 64)
    val stopId: String,

    @Column(name = "arrival_seconds_after_midnight", nullable = false)
    val arrivalSecondsAfterMidnight: Int,

    @Column(name = "departure_seconds_after_midnight", nullable = false)
    val departureSecondsAfterMidnight: Int,

    @Column(name = "stop_sequence", nullable = false)
    val stopSequence: Int,
) {
    protected constructor() : this(id = "", tripId = "", stopId = "", arrivalSecondsAfterMidnight = 0, departureSecondsAfterMidnight = 0, stopSequence = 0)
}
