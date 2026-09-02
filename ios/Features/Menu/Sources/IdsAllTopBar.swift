import SwiftUI
import CoreDesignSystem

// Was a text navbar -- "ID | Support | Settings" with pipe separators, then a hamburger
// icon leading to the Menu screen -- neither has an equivalent in real Toss. The
// Explore tab top bar is just the user's name plus a settings icon; support/ID live
// as rows further down the list, not up here. The profile icon this bar showed
// 2026-07-24 - 2026-08-10 is gone -- You is its own primary tab now (see
// ContentView.swift's Home/Pay/Explore/Messages/You), so a second way to reach the
// same screen from here would be a real duplicate, not a convenience (same fix as
// Android's AllTopBar).
//
// Moved here from App/Sources/BenefitsShopAllScreens.swift (2026-09-02, Menu
// Feature-module decomposition) -- its only real caller, EntireMenuScreen, moved
// here too.
struct IdsAllTopBar: View {
    var onOpenSettings: () -> Void = {}

    var body: some View {
        HStack {
            Text("TUYIZERE ERIC")
                .font(IDS.scaledFont(size: 26, weight: .bold, relativeTo: .largeTitle))
                .foregroundColor(IDS.Colors.textPrimary)
            Spacer()
            // Real Settings screen (2026-07-12, see SettingsScreen.swift) --
            // previously wired directly to logout with no screen behind it at all,
            // same fix as Android's AllTopBar.
            Button(action: onOpenSettings) {
                Image(systemName: "gearshape")
                    .font(IDS.scaledFont(size: 20, weight: .regular, relativeTo: .body))
                    .foregroundColor(IDS.Colors.textPrimary)
            }
            .buttonStyle(PressScaleButtonStyle())
            // Found live via FocusOrderTests (2026-07-12): this button's action and
            // icon were changed from direct-logout to opening the real Settings
            // screen, but the accessibility label was never updated to match -- a
            // VoiceOver user would have been told "Log out" for a button that
            // actually opens Settings.
            .accessibilityLabel("Settings")
        }
    }
}
