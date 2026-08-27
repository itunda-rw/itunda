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
    // For RateLimiter -- MerchantService.chargeCard's demo card-authorization endpoint
    // needs the same real anti-abuse rate limit PartnerService/CertificateService
    // already use, see MerchantService.kt's own doc comment on that call site.
    implementation(project(":auth"))
    // For AutoTopUpService.topUpShortfall -- MerchantService.collect reuses the same
    // real Naver Pay Money "결제 시 부족분 자동 충전" mechanic P2pService.sendDirect
    // already uses, see MerchantService.kt's own doc comment on that call site. No
    // circular dependency: :account only depends on :core.
    implementation(project(":account"))
    // For MessagingService.startOrGetConversation -- ShoppingController's real
    // contact-seller endpoint reuses the same generic 1:1 messaging system
    // MarketplaceController's own contact-seller already uses, see
    // marketplace/build.gradle.kts's own :messaging dependency for the same shape. No
    // circular dependency: :messaging only depends on :core and :auth.
    implementation(project(":messaging"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.security:spring-security-core")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Same Kotest + MockK convention as :core's tests -- see LedgerServiceTest.kt's
    // doc comment for why (Toss's own documented Kotlin testing convention).
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("io.mockk:mockk:1.13.12")
}
