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
    // For RateLimiter -- real anti-spam limit on P2P request creation/payment, found
    // missing in a 2026-07-19 security sweep, same convention every other
    // content/money-creation endpoint in this codebase already uses.
    implementation(project(":auth"))
    // For RoundUpService -- real round-up auto-saving after a real P2P transfer
    // (2026-07-25), see RoundUpSettings.kt's own doc comment. Same cross-module reuse
    // discipline MarketplaceService's own dependency on the messaging module already
    // established -- itunda's backend modules aren't isolated from each other the way
    // the Android client's own Toss-Microfeatures split is.
    implementation(project(":savings"))
    // For FamilyLinkService -- real daily spend-limit enforcement on a child's own P2P
    // sends (2026-07-27), see FamilyLink.kt's own doc comment. :family only depends on
    // :core/:auth, so this is not circular.
    implementation(project(":family"))
    // For AutoTopUpService -- real Naver Pay Money "결제 시 부족분 자동 충전" (shortfall
    // auto-charge at payment time), 2026-07-27, see AutoTopUpService.topUpShortfall's
    // own doc comment. :wallet only depends on :core, so this is not circular.
    implementation(project(":wallet"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.security:spring-security-core")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("io.mockk:mockk:1.13.12")
}
