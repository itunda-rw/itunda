package rw.itunda.core.pricing

import java.math.BigDecimal

/**
 * Real gap found+fixed 2026-09-06 -- a repo-wide duplicated-helper-style sweep found
 * `BigDecimal("0.015")` independently re-declared in 12 different files, each one's own
 * doc comment explicitly saying "same real fee-schedule reasoning [some other file]
 * already establishes -- reused rather than inventing a Nth number" (OrderService/
 * GiftVoucherService/MerchantService/MerchantBillingChargeExecutor's account-to-account
 * merchant collection; MarketplaceService's escrow hold; VehicleInspectionService/
 * MerchantBookingService's held-then-released deposit; RideTripService/
 * DesignatedDriverService's fare; DineInOrderService/EatsOrderService's order
 * collection; ForeignCurrencyAccountService's FX margin). All 12 already agreed on the
 * exact same value with zero divergence -- no live bug -- but 12 independent copies of
 * one deliberate business decision is exactly the standing future-drift risk this
 * codebase already hit once for real (the Android/iOS/backend money-formatting
 * thousands-separator bug, see `project_itunda_money_formatting_sweep` session memory):
 * a future rate change landing in only SOME of the 12 files would silently create a real
 * inconsistency. Consolidated here so every caller shares one source of truth.
 *
 * Sourced (see the original `MerchantService.feeRate` doc comment this traces back
 * to, and `docs/TOSS_ARCHITECTURE_FACTS.md`'s own Toss Payments SDK research): Toss
 * Payments' real published fee schedule tiers account-based payments ("Toss Pay") at
 * 0.8%-1.8% depending on merchant volume. No tiering system exists here, so a single
 * flat rate in the middle of that real range is used, rather than inventing a number
 * the way an old, now-corrected `MERCHANT_SERVICES.md` spec's "QR payments: 1.5%" once
 * did independently. Applied uniformly to every real account-to-account collection/
 * escrow/deposit/fare/FX-margin feature rather than a separately-tunable rate per
 * feature. `MerchantService.feeRate` itself is only ever the DEFAULT -- a real
 * per-merchant `feeRateOverride` can still take precedence, unaffected by this
 * consolidation.
 *
 * `AgentCommissionSchedule.TIER_3_RATE` is a real, deliberate EXCEPTION, not a 13th
 * missed instance: it's a genuinely different concept (the middle tier of a 4-tier
 * mobile-money-agent commission schedule, sourced independently), which happens to
 * share the same numeric value today by coincidence, not by shared intent -- correctly
 * left as its own independent constant.
 */
object PlatformFees {
    val PLATFORM_FEE_RATE: BigDecimal = BigDecimal("0.015")
}
