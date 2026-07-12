package run.granite.brownfield

import com.brickmodule.BrickModuleSpec
import com.facebook.react.bridge.ReactContext

/**
 * Real, single-arg-constructor `GraniteBrownfieldModule` (2026-07-12,
 * granite-adoption stage 7) -- satisfies `brick_modules.gradle`'s own
 * auto-generated `BrickModulesList.kt`
 * (android/app/build/generated/source/brick-provider/BrickModulesList.kt,
 * regenerated at build time), which expects a real class at exactly this
 * name/package with a `(context: ReactContext)` constructor, per the
 * `brickModule.android.moduleName` field in
 * `@granite-js/brownfield-module`'s own package.json.
 *
 * Itunda's real, per-mini-app registration path
 * (`rw.itunda.app.miniapps.SaroniteMiniAppActivity.brickModuleRegistry`, and
 * that path's own `GraniteBrownfieldModuleImpl`) does NOT use
 * `BrickModulesList` -- it needs a real `Activity` reference to close and a
 * scheme scoped to the specific mini-app, neither of which this generic,
 * ReactContext-only convenience constructor can express. This class exists
 * only so the generated helper compiles; nothing in itunda's own code calls
 * it. `closeView()` falls back to `reactContext.currentActivity` (the best
 * available without a captured Activity reference); `getSchemeUri()` returns
 * itunda's base scheme, not a per-mini-app one.
 */
class GraniteBrownfieldModule(
    reactContext: ReactContext,
) : BrickModuleSpec(reactContext), GraniteBrownfieldModuleSpec {

    override val moduleName: String = GraniteBrownfieldModuleSpec.MODULE_NAME

    override fun getSchemeUri(): String = "itunda://saronite"

    override suspend fun closeView() {
        getReactContext().currentActivity?.finish()
    }
}
