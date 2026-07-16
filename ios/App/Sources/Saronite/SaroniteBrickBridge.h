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

@end

NS_ASSUME_NONNULL_END
