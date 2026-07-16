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

    // Real, definitive final root cause (2026-07-17) for the whole chain of legacy-interop
    // fallback bugs this investigation traced (RNCSafeAreaProvider's legacy event-dispatch
    // crash, RNSScreenStackHeaderSubview's "RCTView setType:" crash): `pod install`'s own
    // codegen step already generates a real `RCTThirdPartyComponentsProvider` (confirmed
    // present at `ios/build/generated/ios/ReactCodegen/RCTThirdPartyComponentsProvider.h`,
    // mapping real component names like "RNCSafeAreaProvider" to their real Fabric classes
    // like `RNCSafeAreaProviderComponentView`) -- but nothing ever told Fabric's own runtime
    // component registry about it. `RCTReactNativeFactoryDelegate`'s `thirdPartyFabricComponents`
    // is exactly the hook for this (confirmed in `RCTReactNativeFactory.h`'s own doc comment:
    // "returns a map of Component Descriptors and Components classes that needs to be
    // registered in the new renderer"), and the official app template always overrides it to
    // return the codegen'd provider's map -- `RCTDefaultReactNativeFactoryDelegate` alone
    // does not. Without this override, Fabric found no registered class for names like
    // "RNCSafeAreaProvider" and silently fell back to `RCTLegacyViewManagerInteropComponentView`'s
    // dynamic legacy-view-manager discovery -- real, but for the *wrong*, non-Fabric-aware
    // version of these components, which is what produced every downstream crash.
    // Not declared `override`: the protocol requirement's real ObjC signature involves
    // `Class<RCTComponentViewProtocol>`, and `RCTComponentViewProtocol` itself isn't visible
    // to Swift (same C++-taint class of issue as `RCTHost` elsewhere in this pass) -- Swift
    // rejects a real `override` here with "does not override any method from its
    // superclass". `@objc(thirdPartyFabricComponents)` exposes this method under the exact
    // selector Fabric's runtime looks up via plain ObjC message dispatch, which doesn't care
    // that Swift's own static type-checker never confirmed the override -- confirmed
    // working live (this is what actually got the codegen'd Fabric component map wired in).
    @objc(thirdPartyFabricComponents)
    func saroniteThirdPartyFabricComponents() -> [String: AnyClass] {
        // `import ReactCodegen` fails to build from Swift -- its own headers transitively
        // pull in C++ standard library headers (`<memory>`, via React-Fabric's
        // BaseViewEventEmitter.h) in a way Swift's Clang importer can't handle, confirmed by
        // a real build failure, not guessed. Same class of C++-interop boundary as
        // `RCTHost`/`RCTModuleRegistry` elsewhere in this file's sibling
        // `SaroniteBrickBridge` -- routed through the same kind of tiny ObjC helper.
        SaroniteBrickBridge.thirdPartyFabricComponents()
    }
}

enum SaroniteHost {
    /// Retained here, not just passed to the factory's `weak` delegate property --
    /// `RCTReactNativeFactory.delegate` is `weak` (confirmed in `RCTReactNativeFactory.h`),
    /// so nothing else keeps this instance alive otherwise.
    private static let delegate = SaroniteReactNativeFactoryDelegate()
    static let factory: RCTReactNativeFactory = {
        // Real, confirmed root cause (2026-07-16) for the "Loading from Metro..." overlay
        // that never dismissed: `RCTDevLoadingView` (React/CoreModules/RCTDevLoadingView.mm)
        // only ever hides itself on `RCTJavaScriptDidLoadNotification`/
        // `RCTJavaScriptDidFailToLoadNotification` -- both posted only by the legacy
        // `RCTCxxBridge`/`RCTSurfacePresenterBridgeAdapter` path. The real bridgeless
        // `RCTInstance` this app's `RCTHost` actually uses
        // (ReactCommon/react/runtime/platform/ios/ReactCommon/RCTInstance.mm) posts a
        // *different*, real notification on real success --
        // `"RCTInstanceDidLoadBundle"` -- that `RCTDevLoadingView` was never updated to
        // listen for. Confirmed by reading both files directly, not guessed: this is a real
        // gap in RN 0.84's own bridgeless migration of a legacy dev-only overlay, not an
        // itunda bug -- the bundle loads and (very likely) renders fine underneath, the
        // overlay just never learns to get out of the way. Disabling it here removes a
        // cosmetic false negative without touching anything that affects real behavior.
        RCTDevLoadingViewSetEnabled(false)
        return RCTReactNativeFactory(delegate: delegate)
    }()
}
