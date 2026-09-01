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
    ":app"
)
