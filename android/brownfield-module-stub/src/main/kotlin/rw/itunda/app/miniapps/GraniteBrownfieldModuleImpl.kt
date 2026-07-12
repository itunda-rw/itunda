package rw.itunda.app.miniapps

import android.app.Activity
import com.brickmodule.BrickModuleSpec
import com.facebook.react.bridge.ReactContext
import run.granite.brownfield.GraniteBrownfieldModuleSpec

/**
 * Itunda's own real implementation of granite's generic, open-source
 * `GraniteBrownfieldModuleSpec` (2026-07-12, granite-adoption stage 7) --
 * the actual concrete class the real, open-source spec
 * (`packages/saronite/node_modules/@granite-js/brownfield-module`) explicitly
 * leaves unimplemented: per `packages/saronite/README.md`'s own sourced
 * account, Granite-the-open-source-project ships the generic spec/interface;
 * the real native implementation is each host app's own, same as Toss's own
 * private implementation behind Apps in Toss.
 *
 * Extends `BrickModuleSpec` (not just the `GraniteBrownfieldModuleSpec`
 * interface directly) to get `emitEvent`'s real `eventEmitterCallback`
 * wiring for free -- `onVisibilityChanged` isn't wired to a real Android
 * lifecycle signal yet (itunda's mini-apps don't currently depend on it);
 * left as a known, honest gap rather than faked.
 */
class GraniteBrownfieldModuleImpl(
    reactContext: ReactContext,
    private val getActivity: () -> Activity?,
    private val scheme: String,
) : BrickModuleSpec(reactContext), GraniteBrownfieldModuleSpec {

    override val moduleName: String = GraniteBrownfieldModuleSpec.MODULE_NAME

    // schemeUri comes from GraniteBrownfieldModuleSpec's own default
    // implementation (delegates to getSchemeUri() below) -- no override needed.
    override fun getSchemeUri(): String = scheme

    override suspend fun closeView() {
        getActivity()?.finish()
    }
}
