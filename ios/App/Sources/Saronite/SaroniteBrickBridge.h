#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/// Pure-ObjC-runtime helper for reaching real granite's `BrickModule` TurboModule
/// instance from Swift. Exists because `RCTRootViewFactory.reactHost` (real, declared
/// in `RCTRootViewFactory.h`) is invisible to Swift -- its type, `RCTHost`, is only
/// declared under `#if defined(__cplusplus)` in RN's own headers, which Swift's Clang
/// importer silently drops rather than errors on. Plain KVC (`-valueForKey:`) reaches
/// the same real property/method by name at runtime without ever needing a Swift- or
/// even ObjC-visible declaration of `RCTHost`/`RCTModuleRegistry` (neither ships a
/// usable public header at all -- confirmed by a direct search of the installed
/// `react-native` package and `Pods/Headers`), the same reasoning
/// `SaroniteMiniAppViewController`'s own header comment gives for using `-moduleForName:`
/// this way.
@interface SaroniteBrickBridge : NSObject

+ (nullable id)brickModuleFromRootViewFactory:(id)rootViewFactory;

/// `RCTRootViewFactory.viewWithModuleName:` alone was not enough to get a real mini-app
/// screen to render live (confirmed: a real root view attaches with its own real default
/// background, but zero content ever mounts and zero network calls ever fire) --
/// `RCTHost.start` (real, declared in `RCTHost.h`, same Swift-invisibility reasoning as
/// `reactHost` above) genuinely needs to be called explicitly for a brownfield host like
/// this one; it is not implied by view creation alone the way `RCTRootViewFactory.h`'s own
/// doc comment ("creates new RCTRootViews on demand") suggested.
+ (void)startReactHost:(id)rootViewFactory;

/// Real, definitive final root cause (2026-07-17) for the whole chain of legacy-interop
/// fallback bugs this investigation traced: `pod install`'s own codegen step already
/// generates a real `RCTThirdPartyComponentsProvider` mapping real component names (e.g.
/// "RNCSafeAreaProvider") to their real Fabric classes -- but nothing told Fabric's
/// runtime registry about it, so it silently fell back to dynamic legacy-view-manager
/// discovery for the *wrong*, non-Fabric-aware versions of these components. `import
/// ReactCodegen` fails to build from Swift (its headers transitively pull in C++ standard
/// library headers in a way Swift's Clang importer can't handle, confirmed by a real build
/// failure) -- routed through this ObjC helper instead, same reasoning as this class's
/// other methods.
+ (NSDictionary<NSString *, Class> *)thirdPartyFabricComponents;

@end

NS_ASSUME_NONNULL_END
