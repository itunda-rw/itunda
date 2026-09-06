import Foundation

/// Real gap found+fixed 2026-09-06, same shape as this session's own Android
/// (`core/designsystem`'s `UpfrontDepositPolicy.kt`) and backend
/// (`core/pricing/PlatformFees`/`TipPolicy`/`ReminderWindows`/`CancellationPolicy`)
/// duplicated-constant sweeps: itunda's real 12-month upfront-interest deposit rate
/// (2.80%/yr) was independently re-declared THREE times on iOS -- as a real numeric
/// constant in `UpfrontDepositScreen.swift` (used in a real calculation,
/// `amount * annualRate / 100`), and as two separate hardcoded "2.80%/yr" display
/// strings in `EntireMenuScreenCatalog.swift`/`EntireMenuScreen.swift`. All 3 already
/// agreed on the same value -- no live divergence -- but the same standing
/// future-drift risk found repeatedly this session on the backend and Android: a
/// future rate change landing in only some of the 3 places would show a genuinely
/// different rate on the menu list than the actual deposit screen it links to.
public let upfrontDepositAnnualRate: Double = 2.80
