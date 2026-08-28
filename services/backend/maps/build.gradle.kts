plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.4")
    }
}

dependencies {
    implementation(project(":core"))
    // For RateLimiter -- same anti-spam convention every other user-facing endpoint in
    // this codebase already has (search/directions are cheap to call in a loop otherwise).
    implementation(project(":auth"))
    // Real consolidated place-detail endpoint (itunda Maps redesign, 2026-08-28) --
    // MapsPlaceDetailService reuses EatsReviewService's real rating/tag data and
    // MerchantUpdateService's real news feed directly rather than duplicating them.
    // Confirmed no circular module dependency first: neither :eats nor :merchant
    // depends on :maps.
    implementation(project(":eats"))
    implementation(project(":merchant"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.security:spring-security-core")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Same Kotest + MockK convention as :core's/:marketplace's/:messaging's tests.
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("io.mockk:mockk:1.13.12")
}
