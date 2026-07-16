#import "SaroniteBrickBridge.h"

// `#import <ReactCodegen/RCTThirdPartyComponentsProvider.h>` (both modular and plain
// textual form) fails to build here -- Clang tries to build `ReactCodegen` as a whole
// module even for a textual include, and that module's own transitive headers
// (React-Fabric's BaseViewEventEmitter.h) can't find `<memory>` in that specific
// module-compilation context, confirmed by a real, repeatable build failure, not guessed.
// `RCTThirdPartyComponentsProvider` is still a real, compiled, linked class (it's part of
// the same Pods build ItundaApp already links against) -- reached here via
// `NSClassFromString` + a plain class-method message send instead, the same technique
// (and the same reasoning) as `brickModuleFromRootViewFactory:`/`startReactHost:` above use
// for `RCTHost`/`RCTModuleRegistry`.
@protocol SaroniteThirdPartyComponentsProviderLookup <NSObject>
+ (NSDictionary<NSString *, Class> *)thirdPartyFabricComponents;
@end

// `RCTModuleRegistry` (the real runtime type of `RCTHost.moduleRegistry`) ships no public
// header anywhere in the installed `react-native` package -- confirmed by a direct search --
// even though `-moduleForName:` is a real, compiled, exported method (`RCTModuleRegistry.m`'s
// own implementation, used internally by RN's `RCTBridgeProxy`). Declaring just the one real
// selector this needs via a protocol lets the compiler emit a normal, correctly-typed
// Objective-C message send (unlike `performSelector:`, which only supports object-typed
// arguments and would misinterpret the `const char *` this method actually takes).
@protocol SaroniteModuleRegistryLookup <NSObject>
- (nullable id)moduleForName:(const char *)moduleName;
@end

@protocol SaroniteReactHostLifecycle <NSObject>
- (void)start;
@end

@implementation SaroniteBrickBridge

+ (nullable id)brickModuleFromRootViewFactory:(id)rootViewFactory {
    if (!rootViewFactory) {
        return nil;
    }
    id reactHost = [rootViewFactory valueForKey:@"reactHost"];
    if (!reactHost) {
        return nil;
    }
    id moduleRegistry = [reactHost valueForKey:@"moduleRegistry"];
    if (![moduleRegistry respondsToSelector:@selector(moduleForName:)]) {
        return nil;
    }
    id<SaroniteModuleRegistryLookup> lookup = moduleRegistry;
    return [lookup moduleForName:"BrickModule"];
}

+ (void)startReactHost:(id)rootViewFactory {
    if (!rootViewFactory) {
        return;
    }
    // Real, live-confirmed finding (2026-07-16, via temporary NSLog diagnostics): `reactHost`
    // is nil here unless `view(withModuleName:)` has already been called at least once on
    // this same `rootViewFactory` -- it creates the `RCTHost` lazily, on first view request.
    // Callers must create the root view before calling this, not after (see
    // SaroniteMiniAppViewController.swift's own comment on why that ordering mattered).
    id reactHost = [rootViewFactory valueForKey:@"reactHost"];
    if (!reactHost || ![reactHost respondsToSelector:@selector(start)]) {
        return;
    }
    id<SaroniteReactHostLifecycle> lifecycle = reactHost;
    [lifecycle start];
}

+ (NSDictionary<NSString *, Class> *)thirdPartyFabricComponents {
    Class<SaroniteThirdPartyComponentsProviderLookup> providerClass = NSClassFromString(@"RCTThirdPartyComponentsProvider");
    if (!providerClass || ![providerClass respondsToSelector:@selector(thirdPartyFabricComponents)]) {
        return @{};
    }
    return [providerClass thirdPartyFabricComponents] ?: @{};
}

@end
