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

    @Test
    fun `feature impl modules depend on their own api module`() {
        val features = listOf(
            "payments", "marketplace", "jobs", "property", "ride", "community",
            "shop", "eats", "talk", "maps", "bills", "merchant", "credit",
            "wealth", "insurance", "engagement", "assets", "banking", "home",
            "pay", "menu", "my",
        )

        val missing = features.mapNotNull { feature ->
            val gradleFile = java.io.File(
                java.io.File("."),
                "features/$feature/impl/build.gradle.kts",
            )
            if (!gradleFile.isFile) {
                "$feature: missing impl/build.gradle.kts"
            } else {
                val expected = """implementation(project(":features:$feature:api"))"""
                if (expected !in gradleFile.readText()) {
                    "$feature: missing $expected"
                } else null
            }
        }

        assertTrue(
            missing.isEmpty(),
            "Feature impl modules must explicitly depend on their own api modules:\n" +
                missing.joinToString("\n"),
        )
    }

    @Test
    fun `app shell feature imports are composition entry points`() {
        val appScreen = java.io.File(
            java.io.File("."),
            "app/src/main/java/rw/itunda/app/ui/ItundaAppScreen.kt",
        )

        val violations = appScreen.readText()
            .lineSequence()
            .filter { it.startsWith("import rw.itunda.feature.") }
            .filter { ".impl." in it && !it.trimEnd().endsWith("EntryPoint") }
            .toList()

        assertTrue(
            violations.isEmpty(),
            "App shell must consume feature implementations through explicit *EntryPoint " +
                "composition functions, not concrete screen/content imports:\n" +
                violations.joinToString("\n"),
        )
    }

    @Test
    fun `app shell uses feature composition entry points for primary tabs`() {
        val appScreen = java.io.File(
            java.io.File("."),
            "app/src/main/java/rw/itunda/app/ui/ItundaAppScreen.kt",
        )

        assertTrue(appScreen.isFile, "Expected native app shell source: ${appScreen.path}")

        val imports = appScreen.readText()
            .lineSequence()
            .filter { it.startsWith("import rw.itunda.feature.") }
            .toList()

        val required = mapOf(
            "home" to "HomeEntryPoint",
            "pay" to "PayEntryPoint",
            "menu" to "MenuEntryPoint",
            "my" to "MyEntryPoint",
            "talk" to "TalkEntryPoint",
            "shop" to "CommerceShopEntryPoint",
            "eats" to "EatsEntryPoint",
            "maps" to "MapsEntryPoint",
            "wealth" to "InvestEntryPoint",
            "credit" to "LoansEntryPoint",
            "banking" to "BankHubEntryPoint",
            "ride" to "RideEntryPoint",
        )

        val violations = required.mapNotNull { (feature, entryPoint) ->
            val concreteImports = imports.filter {
                it.startsWith("import rw.itunda.feature.$feature.impl.")
            }
            when {
                imports.any { it == "import rw.itunda.feature.$feature.impl.$entryPoint" } -> null
                concreteImports.isEmpty() -> "$feature: missing $entryPoint import"
                else -> "$feature: app shell still imports concrete impl(s): ${concreteImports.joinToString()}"
            }
        }

        assertTrue(
            violations.isEmpty(),
            "Primary feature tabs must enter through feature composition entry points:\n" +
                violations.joinToString("\n"),
        )
    }
}
