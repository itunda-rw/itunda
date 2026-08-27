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
    implementation(project(":auth"))
    // Real Uber "Share Trip Status" (2026-08-16) -- see RideTripService.shareTripStatus's
    // own doc comment. Mirrors EatsFavoriteService.shareFavoritesToConversation's exact
    // existing use of MessagingService.
    implementation(project(":messaging"))
    // Real moto-taxi fare tap-collection (2026-08-27) -- see MotoFareService.kt's own
    // doc comment. Needs AutoTopUpService for the same real "top up itunda Pay money
    // from Bank if short" default MerchantService.chargeByCustomerCode already
    // establishes for a customer-presented-code charge.
    implementation(project(":account"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.security:spring-security-core")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Same Kotest + MockK convention as :core's/:eats'/:merchant's tests.
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("io.mockk:mockk:1.13.12")
}
