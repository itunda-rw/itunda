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
    // For RateLimiter -- real anti-spam limit on savings-goal creation, found missing
    // in a 2026-07-19 security sweep (same bug shape as the P2P/chargeCard/reactions
    // findings before it: real, free, unbounded row creation with zero protection).
    implementation(project(":auth"))
    // For StocksService -- real round-up-to-invest (2026-07-27), see RoundUpSettings.kt's
    // own doc comment. stocks only depends on :core, so this is not circular.
    implementation(project(":stocks"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.security:spring-security-core")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("io.mockk:mockk:1.13.12")
}
