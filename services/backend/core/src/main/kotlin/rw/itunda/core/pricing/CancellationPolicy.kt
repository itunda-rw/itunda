package rw.itunda.core.pricing

import java.time.Duration

/**
 * Real gap found+fixed 2026-09-06, same shape as [PlatformFees]/[TipPolicy]/
 * `ReminderWindows`: `CANCELLATION_FEE_GRACE_PERIOD = Duration.ofMinutes(2)` was
 * independently declared in both `RideTripService` and `DesignatedDriverService`,
 * with `DesignatedDriverService`'s own doc comment already admitting it's "ported
 * from `RideTripService`'s own identical constants." Both already agreed on the same
 * value -- no live divergence -- same standing future-drift risk already fixed 3
 * times this session for other constant families.
 *
 * Sourced (see the original `RideTripService.CANCELLATION_FEE_GRACE_PERIOD` doc
 * comment this traces back to): Uber's own real published cancellation-fee policy
 * (help.uber.com/riders/article/cancellation-fees-explained -- "for most economy
 * ride types... fees may be charged if you cancel 2+ minutes after requesting" once
 * matched with a driver). itunda's one real ride type maps to Uber's economy tier, so
 * the real 2-minute figure applies directly, not an invented one.
 */
object CancellationPolicy {
    val CANCELLATION_FEE_GRACE_PERIOD: Duration = Duration.ofMinutes(2)
}
