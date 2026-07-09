pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "itunda"

include(":app")

// Core Bounded Contexts
include(":core:designsystem")
include(":core:network")
include(":core:testing")
include(":core:identity")
include(":core:consent")
include(":core:ledger")
include(":core:risk")

// Feature Bounded Contexts
include(":features:payments:api")
include(":features:payments:impl")
include(":features:payments:testing")

include(":features:bills:api")
include(":features:bills:impl")
include(":features:bills:testing")

include(":features:merchant:api")
include(":features:merchant:impl")
include(":features:merchant:testing")

include(":features:credit:api")
include(":features:credit:impl")
include(":features:credit:testing")

include(":features:wealth:api")
include(":features:wealth:impl")
include(":features:wealth:testing")

include(":features:insurance:api")
include(":features:insurance:impl")
include(":features:insurance:testing")

include(":features:engagement:api")
include(":features:engagement:impl")
include(":features:engagement:testing")

include(":features:assets:api")
include(":features:assets:impl")
include(":features:assets:testing")

include(":features:banking:api")
include(":features:banking:impl")
include(":features:banking:testing")

// Itunda Pay SDK (For 3rd party integrations)
include(":sdk:pay")
