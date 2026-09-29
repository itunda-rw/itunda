import SwiftUI
import FeatureCredit

// Real per-feature isolated preview app (2026-08-29) -- see FeatureBankingExampleApp
// for the full rationale (docs/TOSS_ARCHITECTURE_FACTS.md §8). Credit has exactly one
// public screen, so this app is a direct entry point rather than a picker list.
// CreditScoreScreenView calls the real NetworkClient.shared.getCreditScore()
// internally (see its own doc comment: "only needs CoreDesignSystem and
// CoreNetwork") -- against a dev backend that is typically unreachable from this
// isolated Example build, it renders its own real error state rather than crashing,
// which is itself useful to see in isolation.
@main
struct FeatureCreditExampleApp: App {
    var body: some Scene {
        WindowGroup {
            CreditScoreScreenView()
        }
    }
}
