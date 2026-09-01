rootProject.name = "itunda-backend"

include(
    ":core",
    ":auth",
    ":account",
    ":bills",
    ":loans",
    ":stocks",
    ":savings",
    ":insurance",
    ":notifications",
    ":discover",
    ":analytics",
    ":contacts",
    ":system",
    ":merchant",
    ":identity",
    ":partners",
    ":certificate",
    ":rewards",
    ":creditscore",
    ":trustscore",
    ":overview",
    ":p2p",
    ":offline",
    ":support",
    ":messaging",
    ":marketplace",
    ":community",
    ":knowledge",
    ":jobs",
    ":realestate",
    ":commerce",
    ":eats",
    ":maps",
    ":gift",
    ":splitbill",
    ":calling",
    ":agents",
    ":rideshare",
    ":vehicle",
    ":family",
    ":card",
    ":transit",
    ":ussd",
    // Real, distinct module (2026-09-01) -- see security/build.gradle.kts's own doc
    // comment: promoted out of :app so card-service (the first independently-
    // deployable product) can share the identical JWT filter chain.
    ":security",
    ":card-service",
    // Real, second independently-deployable module (2026-09-01) -- see
    // insurance-service/build.gradle.kts's own doc comment: :insurance was
    // confirmed to have zero Gradle-level coupling with any other product
    // module, the same property that made :card the first extraction.
    ":insurance-service",
    // Real, third independently-deployable module (2026-09-01) -- see
    // agents-service/build.gradle.kts's own doc comment: :agents (the Agent
    // Operator cash-in/cash-out network) was confirmed to have zero
    // Gradle-level coupling with any other product module.
    ":agents-service",
    // Real, fourth independently-deployable module (2026-09-01) -- see
    // transit-service/build.gradle.kts's own doc comment: :transit (real
    // Kigali stored-value transit + NFC tap-to-collect) was confirmed to have
    // zero Gradle-level coupling with any other product module.
    ":transit-service",
    // Real, fifth independently-deployable module (2026-09-01) -- see
    // certificate-service/build.gradle.kts's own doc comment: :certificate was
    // confirmed to have zero Gradle-level coupling with any other product
    // module.
    ":certificate-service",
    // Real, sixth independently-deployable module (2026-09-01) -- see
    // bills-service/build.gradle.kts's own doc comment: :bills was confirmed
    // to have zero Gradle-level coupling with any other product module.
    ":bills-service",
    // Real, seventh independently-deployable module (2026-09-01) -- see
    // vehicle-service/build.gradle.kts's own doc comment: :vehicle was
    // confirmed to have zero Gradle-level coupling with any other product
    // module.
    ":vehicle-service",
    // Real, eighth independently-deployable module (2026-09-01) -- see
    // partners-service/build.gradle.kts's own doc comment: :partners was
    // confirmed to have zero Gradle-level coupling with any other product
    // module.
    ":partners-service",
    // Real, ninth independently-deployable module (2026-09-01) -- see
    // identity-service/build.gradle.kts's own doc comment: :identity was
    // confirmed to have zero Gradle-level coupling with any other product
    // module; its own route-collision hazard with :partners is resolved by
    // gateway registration order (see services/api-gateway/index.js).
    ":identity-service",
    // Real, tenth independently-deployable module (2026-09-01) -- see
    // overview-service/build.gradle.kts's own doc comment: :overview was
    // confirmed to have zero Gradle-level coupling with any other product
    // module.
    ":overview-service",
    // Real, eleventh independently-deployable module (2026-09-01) -- see
    // knowledge-service/build.gradle.kts's own doc comment: :knowledge was
    // confirmed to have zero Gradle-level coupling with any other product
    // module.
    ":knowledge-service",
    ":app"
)
