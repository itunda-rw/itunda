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
    // For RateLimiter.
    implementation(project(":auth"))
    // For P2pService.sendDirect -- real USSD money movement reuses the exact same,
    // already-proven wallet-to-wallet transfer logic every other client uses, rather
    // than reimplementing it. See UssdService's own doc comment.
    implementation(project(":p2p"))
    // For MerchantService.collect -- real Toss Payments ARS결제-style USSD payment
    // completion reuses the exact same, already-proven payment-collection logic every
    // other channel (QR, Face Pay, static QR) already uses. See PaymentIntent.ussdCode's
    // own doc comment.
    implementation(project(":merchant"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.security:spring-security-core")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("io.mockk:mockk:1.13.12")
}
