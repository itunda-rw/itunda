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
    // For ShoppingCashbackService.awardCashback -- real Coupang 정기배송-style
    // subscription discount reuses the exact same real rebate mechanism
    // MerchantService.collect() already established for QR-payment cashback, not a
    // second discount pipeline.
    implementation(project(":merchant"))
    // For AutoTopUpService.topUpPayFromMain/topUpShortfall -- OrderService.pay now
    // draws from the customer's real itunda Pay money (auto-topped from Bank if
    // short), same as MerchantService.collect()'s own QR-payment treatment, see
    // OrderService.kt's own doc comment. No circular dependency: :account only
    // depends on :core.
    implementation(project(":account"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.security:spring-security-core")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Same Kotest + MockK convention as :core's/:merchant's/:marketplace's tests.
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("io.mockk:mockk:1.13.12")
}
