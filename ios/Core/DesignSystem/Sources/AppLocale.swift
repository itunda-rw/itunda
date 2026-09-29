import Foundation

// Promoted from App/Sources/LoginScreen.swift (2026-08-30) so Feature modules
// (which can't depend back on App) can localize their own screens too --
// OverviewScreenView was the first extraction blocked on this. Android's own
// AppLocale/AppLocalePreference (see LoginScreen.kt's LanguageSwitcher doc
// comment) -- Rwanda's three official languages, not just two.
public enum AppLocale: String { case en, rw, fr }

public let localeStorageKey = "itunda.locale"

public func loadStoredLocale() -> AppLocale {
    if let raw = UserDefaults.standard.string(forKey: localeStorageKey), let locale = AppLocale(rawValue: raw) {
        return locale
    }
    let preferred = Locale.preferredLanguages.first ?? "en"
    if preferred.hasPrefix("rw") { return .rw }
    if preferred.hasPrefix("fr") { return .fr }
    return .en
}
