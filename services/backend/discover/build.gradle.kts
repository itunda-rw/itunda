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
    // Real personalized Discover (2026-08-11) -- see DiscoverService's own doc
    // comment: reuses RewardsService.getTasks' real rewardsTotal for the itunda
    // Points badge instead of a hardcoded string, same cross-feature-module
    // dependency pattern p2p->savings/commerce->merchant already establish.
    implementation(project(":rewards"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.security:spring-security-core")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
