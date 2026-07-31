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
    // For RateLimiter -- real anti-spam limit on post/comment creation and the like
    // toggle, same convention every other user-content-creation endpoint in this
    // codebase uses (Marketplace, Partner SDK, Certificate, messaging reactions).
    implementation(project(":auth"))
    // For SplitBillService -- real 당근마켓 같이사요 (group-buy) cost-splitting reuses
    // the already-proven SplitBill mechanic outright rather than reinventing it. No
    // cycle: splitbill depends on messaging/core/auth, never on community.
    implementation(project(":splitbill"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.security:spring-security-core")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("io.mockk:mockk:1.13.12")
}
