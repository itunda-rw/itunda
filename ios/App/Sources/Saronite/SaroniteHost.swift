import Foundation
import React
import React_RCTAppDelegate

/// Real granite/RN brownfield host for iOS -- the "Apps in Itunda" mini-app runtime,
/// mirroring Android's `ItundaApplication` (`ReactHost`). Unlike Android, this app has no
/// `UIApplicationDelegate` at all (`ItundaApp.swift` is pure SwiftUI); the RN runtime is
/// never started eagerly for the whole app, only lazily the first time a mini-app screen
/// opens (`SaroniteMiniAppViewController`), matching the same lazy-registration discipline
/// `MiniAppActivity.kt`'s own `brickModuleRegistry` already uses.
///
/// Matches RN 0.84's real official template pattern (`RCTDefaultReactNativeFactoryDelegate`
/// + overriding only `bundleURL()`), confirmed by reading the installed
/// `react-native/Libraries/AppDelegate/*.h` headers directly, not guessed. In `DEBUG`, this
/// points at Metro the same way the official template does (`RCTBundleURLProvider`
/// auto-detects `localhost:8081`, reachable directly from the Simulator, no `adb reverse`
/// equivalent needed); a release build would need a real packaged `main.jsbundle`, not yet
/// produced by any build step in this repo -- same honest scope boundary release iOS
/// mini-app bundling had before this pass.
final class SaroniteReactNativeFactoryDelegate: RCTDefaultReactNativeFactoryDelegate {
    override func bundleURL() -> URL? {
        #if DEBUG
        return RCTBundleURLProvider.sharedSettings().jsBundleURL(forBundleRoot: "index")
        #else
        return Bundle.main.url(forResource: "main", withExtension: "jsbundle")
        #endif
    }
}

enum SaroniteHost {
    /// Retained here, not just passed to the factory's `weak` delegate property --
    /// `RCTReactNativeFactory.delegate` is `weak` (confirmed in `RCTReactNativeFactory.h`),
    /// so nothing else keeps this instance alive otherwise.
    private static let delegate = SaroniteReactNativeFactoryDelegate()
    static let factory = RCTReactNativeFactory(delegate: delegate)
}
