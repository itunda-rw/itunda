package rw.itunda.rideshare

import java.math.BigDecimal

// Real fix (2026-08-26): split out of RideTripService.kt once that file grew past its
// file-size-lint baseline. Pure data classes, no behavior -- zero-risk mechanical
// move, same package so no import changes anywhere.

// Real Kakao T-style multi-stop waypoint input (item 214) -- see RideTripStop.kt's own
// doc comment.
data class RideStopInput(val address: String, val latitude: Double, val longitude: Double)

// Real Uber Driver app-style earnings report (2026-08-16) -- see
// RideTripService.getMyEarnings's own doc comment.
data class DriverEarningsDay(
    val date: java.time.LocalDate,
    val tripCount: Int,
    val grossFare: BigDecimal,
    val platformFees: BigDecimal,
    val netEarnings: BigDecimal,
)
