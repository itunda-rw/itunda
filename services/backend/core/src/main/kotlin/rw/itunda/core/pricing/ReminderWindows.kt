package rw.itunda.core.pricing

import java.time.Duration

/**
 * Real gap found+fixed 2026-09-06, same shape as [PlatformFees]/[TipPolicy]:
 * `Duration.ofDays(3)` was independently re-declared as a pre-expiry/pre-due-date
 * reminder window in 4 files, each one's own doc comment explicitly cross-referencing
 * another as the source ("Same real window EatsMembershipService.REMINDER_WINDOW
 * already established" / "Same 3-day window MerchantCouponService
 * .EXPIRY_REMINDER_WINDOW already established" / "Same real 3-day window this
 * codebase's other pre-deadline reminders... already use"). All 4 already agreed on
 * the same value -- no live divergence -- but the same standing future-drift risk
 * already fixed twice this session for other constant families.
 *
 * Sourced (see the original `MerchantCouponService.EXPIRY_REMINDER_WINDOW` doc
 * comment this traces back to): itunda's own honest scoping choice -- no single real
 * external reference publishes one universal "remind N days before a paid perk/
 * payment is due" number, so 3 days was chosen once and then deliberately reused
 * everywhere this same shape of reminder applies, shorter than the unrelated 7/30/60-day
 * windows used elsewhere in this codebase for longer-lived real-world documents
 * (`GiftVoucher.EXPIRY_REMINDER_WINDOW`, `InsurancePolicy`, `Certificate` -- those are
 * NOT part of this family, correctly left as their own separate constants).
 *
 * `VupLoanService.getLoansDueSoonForReminder`'s own `withinDays: Long = 3` default
 * parameter is the same real 3-day policy in a different shape (a function default,
 * not a shared `val`) -- a real, named follow-up candidate for a future pass, not
 * consolidated here to avoid changing that function's public signature in the same
 * pass as this purely-internal refactor.
 */
object ReminderWindows {
    val PRE_EXPIRY_REMINDER_WINDOW: Duration = Duration.ofDays(3)
}
