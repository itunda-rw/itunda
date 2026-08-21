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
    // For SplitBillService.createDirectSplitBill -- real 배달의민족 함께주문 Dutch-pay
    // requests after a group order is finalized, see GroupEatsOrderService.kt's own doc
    // comment.
    implementation(project(":splitbill"))
    // Real "message restaurant" (2026-08-16, Uber Eats-sourced) -- see
    // EatsController.contactRestaurant's own doc comment. Mirrors
    // MarketplaceService.contactSeller's exact existing use of MessagingService.
    implementation(project(":messaging"))
    // For AutoTopUpService.topUpPayFromMain/topUpShortfall -- EatsOrderService/
    // DineInOrderService now draw from the customer's real itunda Pay money
    // (auto-topped from Bank if short) at order payment time, same as
    // MerchantService.collect()'s own QR-payment treatment. No circular
    // dependency: :account only depends on :core.
    implementation(project(":account"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.security:spring-security-core")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Same Kotest + MockK convention as :core's/:commerce's/:marketplace's tests.
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("io.mockk:mockk:1.13.12")
}
