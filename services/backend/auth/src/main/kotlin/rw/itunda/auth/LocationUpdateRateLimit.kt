package rw.itunda.auth

import java.time.Duration

/**
 * Real gap found+fixed 2026-09-06, same shape as this session's own `core/pricing`/
 * `core/ai` duplicated-constant sweeps: the `limit = 20, window = Duration.ofMinutes(1)`
 * pair passed to `rateLimiter.checkLimit(...)` for a real-time GPS location-push
 * endpoint was independently re-declared, byte-for-byte identical, as inline literal
 * arguments at 6 call sites -- `RiderService.updateLocation` (the real origin, its own
 * doc comment sourcing the number), `RideDriverService.updateLocation`,
 * `DesignatedDriverService.updateLocation`, `BikeRentalService.updateLocation`,
 * `LiveLocationShareService.updateLocation` -- each one's own doc comment explicitly
 * citing another as "same rate limit... already establishes." All 6 already agreed on
 * the same value -- no live divergence -- but 6 independent inline copies of one
 * deliberate anti-abuse policy is the same standing future-drift risk this session's
 * sweep found repeatedly for named constants, just in inline-literal-argument form
 * instead: a future tuning change landing in only some of the 6 call sites would let
 * an abusive caller write to a different subset of location rows at a different real
 * rate than every sibling endpoint.
 *
 * Sourced (see `RiderService.updateLocation`'s own doc comment, the traced origin): a
 * real Eats delivery rider client pushes a coordinate roughly every 15 seconds during
 * an active delivery (~4/min) -- 20/min comfortably covers that real cadence with
 * headroom while still bounding an abusive caller from writing to this row
 * unboundedly.
 */
object LocationUpdateRateLimit {
    const val LIMIT: Int = 20
    val WINDOW: Duration = Duration.ofMinutes(1)
}
