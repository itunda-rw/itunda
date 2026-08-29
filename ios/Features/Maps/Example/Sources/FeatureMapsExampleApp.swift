import SwiftUI
import FeatureMaps

// Real per-feature isolated preview app (2026-08-29) -- see FeatureBankingExampleApp
// for the full rationale (docs/TOSS_ARCHITECTURE_FACTS.md §8). Maps has exactly one
// public entry screen with a fully-defaulted public init, so this app is a direct
// entry point rather than a picker list. Real self-hosted OSRM/Nominatim/live-location
// calls this screen makes internally will simply fail gracefully in this isolated
// build the same way they would on a real device with no network -- that failure
// path is itself real, reviewable UI, not something this Example app needs to fake.
@main
struct FeatureMapsExampleApp: App {
    var body: some Scene {
        WindowGroup {
            MapScreenView()
        }
    }
}
