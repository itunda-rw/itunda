plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa") version "1.9.22"
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    // Depends on the domain for interfaces and entities
    implementation(project(":payment-service:payment-domain"))
    implementation(project(":core-libs"))
    
    // DB specific dependencies (JPA, MySQL, Outbox pattern)
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("com.mysql:mysql-connector-j")
}

// This module is a library consumed via runtimeOnly(...) at payment-api's entry point
// (see its build.gradle.kts's "DB is runtime only at the entrypoint" comment), not a
// standalone app -- it has no main class. The Spring Boot Gradle plugin defaults to
// building an executable bootJar, which fails without one; build the plain jar instead.
// Fixed 2026-07-11: `./gradlew build` failed on this task before this fix, unrelated
// to any of that day's other changes.
tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}
tasks.getByName<Jar>("jar") {
    enabled = true
}
