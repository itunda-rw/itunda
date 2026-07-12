package run.granite.brownfield

import com.brickmodule.BrickModuleBase

/**
 * Itunda's own corrected replica of the real, vendored
 * `@granite-js/brownfield-module`-generated interface (2026-07-12,
 * granite-adoption stage 7) -- see android/brownfield-module-stub/build.gradle.kts
 * for the full account of the real upstream JVM signature clash this file
 * exists to work around: the vendored interface declares both
 * `val schemeUri: String` (compiles to a JVM `getSchemeUri(): String` method,
 * standard Kotlin property codegen) and an explicit `fun getSchemeUri(): String`
 * -- an identical JVM signature, which the Kotlin compiler genuinely rejects.
 *
 * Both members are real call sites in brick-codegen's own generated
 * `android/.brick/src/main/kotlin/BrickModuleImpl.kt` (regenerated at build
 * time, confirmed by reading it directly: `typedModule.getSchemeUri()` in one
 * generated function, `typedModule.schemeUri` in another) -- so this can't be
 * "fixed" by dropping either member. `@JvmName` doesn't help here: it's
 * rejected on interface members entirely in this Kotlin/JVM target
 * (confirmed: tried on an abstract property, an abstract function, and a
 * default/non-abstract property -- "not applicable to this declaration"
 * every time). `schemeUri` is a top-level Kotlin *extension property*
 * instead of an interface member -- see BrickModuleImplExtensions.kt in this
 * same module for where it's actually declared and why (package matters:
 * it has to live in `com.brickmodule.codegen`, matching the generated
 * caller's own package, not here).
 */
interface GraniteBrownfieldModuleSpec : BrickModuleBase {
    companion object {
        const val MODULE_NAME = "GraniteBrownfieldModule"
    }

    fun getSchemeUri(): String
    suspend fun closeView(): Unit

    override fun getConstants(): Map<String, Any> = mapOf(
        "schemeUri" to getSchemeUri()
    )
}

/**
 * Emit visibilityChanged event to JavaScript -- kept for API parity with the
 * real vendored source, unused today (see GraniteBrownfieldModuleImpl.kt's
 * own note on `onVisibilityChanged` not being wired to a real lifecycle
 * signal yet).
 */
fun GraniteBrownfieldModuleSpec.emitOnVisibilityChanged(payload: Map<String, Any>) {
    if (this is com.brickmodule.BrickModuleSpec) {
        this.emitEvent("onVisibilityChanged", payload)
    }
}
