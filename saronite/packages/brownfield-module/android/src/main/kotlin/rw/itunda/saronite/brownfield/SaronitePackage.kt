package rw.itunda.saronite.brownfield

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager

/**
 * Registers Saronite's bridge with a host app's React Native instance.
 *
 * Usage from the host app (once itunda's `android/app` is actually wired to
 * React Native — not done in this pass, see saronite/README.md):
 *
 * ```kotlin
 * packages.add(SaronitePackage(hostBridge = MyItundaSaroniteHostBridge()))
 * ```
 */
class SaronitePackage(private val hostBridge: SaroniteHostBridge) : ReactPackage {

    override fun createNativeModules(
        reactContext: ReactApplicationContext,
    ): List<NativeModule> = listOf(SaroniteBrownfieldModule(reactContext, hostBridge))

    override fun createViewManagers(
        reactContext: ReactApplicationContext,
    ): List<ViewManager<*, *>> = emptyList()
}
