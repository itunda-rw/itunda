package rw.itunda.architecturetest

import com.lemonappdev.konsist.api.Konsist
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private val FEATURE_IMPL_PACKAGE = Regex("""^rw\.itunda\.feature\.([^.]+)\.impl(\..+)?$""")
private val FEATURE_IMPL_IMPORT = Regex("""^rw\.itunda\.feature\.([^.]+)\.impl(\..+)?""")

/**
 * Enforces the api/impl silo boundary described in docs/MULTI_AGENT_ISOLATION.md:
 * a feature's `impl` package is private to that feature. Other features (and
 * agents working on them in parallel) may only depend on `*.api`.
 */
class FeatureIsolationTest {

    @Test
    fun `feature impl code does not import another feature's impl package`() {
        val implFiles = Konsist
            .scopeFromProject()
            .files
            .filter { file -> file.packagee?.name?.let(FEATURE_IMPL_PACKAGE::matches) == true }

        val violations = implFiles.mapNotNull { file ->
            val ownFeature = FEATURE_IMPL_PACKAGE.find(file.packagee!!.name)!!.groupValues[1]

            val foreignImplImport = file.imports.firstOrNull { import ->
                val match = FEATURE_IMPL_IMPORT.find(import.name)
                match != null && match.groupValues[1] != ownFeature
            }

            foreignImplImport?.let { "${file.path}: imports ${it.name} (feature '$ownFeature' reaching into another feature's impl)" }
        }

        assertTrue(
            violations.isEmpty(),
            "Found ${violations.size} cross-feature impl import(s), which break the silo " +
                "boundary in docs/MULTI_AGENT_ISOLATION.md. Depend on the other feature's " +
                "`api` module instead:\n" + violations.joinToString("\n"),
        )
    }
}
