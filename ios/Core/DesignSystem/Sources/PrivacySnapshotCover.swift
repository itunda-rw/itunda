import SwiftUI

/// Real App Switcher privacy cover (Bank product-completeness pass, cycle 2,
/// 2026-09-08) -- see each app's own `@main` entry point for the full sourced
/// account (Apple Tech Note QA1838, "Hiding Sensitive Data in the App Switcher").
/// Shown only while `scenePhase == .background`, covering real account balance/
/// transfer/PIN/cash-handling content underneath before UIKit snapshots the scene
/// for the task switcher -- the direct iOS analog of Android's real FLAG_SECURE
/// protection (each app's own `MainActivity.kt`, added 2026-08-09 during a
/// Toss-parity security audit). Shared here, not duplicated per app, since all 4
/// real itunda apps (consumer, Merchant, Rider, Agent) show real financial data.
/// Deliberately plain -- a locked-looking screen, not a loading state, so it never
/// reads as the app being broken if a real user glances at the switcher.
public struct PrivacySnapshotCover: View {
    public init() {}

    public var body: some View {
        ZStack {
            IDS.Colors.backgroundPrimary.ignoresSafeArea()
            VStack(spacing: 12) {
                ZStack {
                    Circle().fill(Color(.tertiarySystemBackground)).frame(width: 76, height: 76)
                    Image(systemName: "lock.fill")
                        .font(IDS.scaledFont(size: 28, weight: .regular, relativeTo: .title1))
                        .foregroundColor(IDS.Colors.brand)
                }
                Text("Itunda").font(.title3).bold().foregroundColor(IDS.Colors.textPrimary)
            }
        }
    }
}
