package rw.itunda.core.pricing

import java.time.Duration

/**
 * Real gap found+fixed 2026-09-08 (correcting the 2026-09-06 consolidation this same
 * object introduced): ride tips and Eats tips are two genuinely DIFFERENT real Uber
 * policies, not one shared value. The 2026-09-06 pass merged them into a single
 * `TIP_WINDOW` on the (wrong) assumption that Eats' own doc comment "admitted" it
 * matched the ride window exactly -- it didn't; Eats' own doc comment separately,
 * correctly cited Uber Eats' real published policy
 * (help.uber.com/en/ubereats/restaurants/article/add-or-change-tip-amount-for-a-past-order
 * -- "you have 40 days to add a tip," re-confirmed live via search since the exact
 * URL's `nodeId` query param had gone stale), a genuinely different real number from
 * the ride-tip source below. Merging them silently cut real Eats buyers' tip window
 * from 40 days to 30 for 2 days before this fix (2026-09-06 to 2026-09-08).
 *
 * - `RIDE_TIP_WINDOW`: Uber's own real published post-trip tipping window
 *   (uber.com/us/en/ride/how-it-works/tips -- "you have 30 days to add a tip in the
 *   app").
 * - `EATS_TIP_WINDOW`: Uber Eats' own real, separately published tipping window (40
 *   days) -- a different product surface with its own real policy, not the same rail
 *   as ride tipping just because both are itunda transfers to a rider account.
 */
object TipPolicy {
    val RIDE_TIP_WINDOW: Duration = Duration.ofDays(30)
    val EATS_TIP_WINDOW: Duration = Duration.ofDays(40)
}
