package rw.itunda.core.pricing

import java.time.Duration

/**
 * Real gap found+fixed 2026-09-06, same shape as [PlatformFees]: `TIP_WINDOW` was
 * independently declared in both `RideTripService` and `EatsOrderService`, with
 * `EatsOrderService`'s own doc comment explicitly admitting it "matches
 * RideTripService.TIP_WINDOW's own real 30-day rule exactly, same real product/team,
 * same rail." Both already agreed on the same value -- no live divergence -- but two
 * independent copies of one deliberate policy is the same standing future-drift risk
 * consolidated here rather than left to coincide by luck a third time.
 *
 * Sourced (see the original `RideTripService.TIP_WINDOW` doc comment this traces back
 * to): Uber's own real published post-trip tipping window
 * (uber.com/us/en/ride/how-it-works/tips -- "you have 30 days to add a tip in the
 * app"), applied identically to itunda's own post-delivery Eats tipping.
 */
object TipPolicy {
    val TIP_WINDOW: Duration = Duration.ofDays(30)
}
