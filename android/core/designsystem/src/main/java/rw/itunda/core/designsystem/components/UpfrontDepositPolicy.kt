package rw.itunda.core.designsystem.components

/**
 * Real gap found+fixed 2026-09-06, same shape as the backend's own duplicated-
 * constant sweep this session (`core/pricing/PlatformFees`/`TipPolicy`/
 * `ReminderWindows`/`CancellationPolicy`): `ANNUAL_RATE = 2.80` (the itunda 12-month
 * upfront-interest deposit's real annual rate) was independently re-declared in both
 * `UpfrontDepositScreen.kt` (`:app`) and `BankHubScreen.kt`
 * (`:features:banking:impl`), the latter's own doc comment already admitting it's
 * "UpfrontDepositScreen's own ANNUAL_RATE already establish[ed], just file-scoped
 * here." Both already agreed on the same value -- no live divergence -- but two
 * independent copies of one deliberate product rate is the same standing
 * future-drift risk this session's backend sweep found 4 real instances of: a future
 * rate change landing in only one of the two files would show a genuinely different
 * rate on the hub summary row than the actual deposit screen it links to.
 */
const val UPFRONT_DEPOSIT_ANNUAL_RATE = 2.80
