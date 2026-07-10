plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":ledger-service:ledger-domain"))
    implementation(project(":core-libs"))

    // DB is purely runtime only at the API entry point
    runtimeOnly(project(":ledger-service:ledger-db"))
    
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework:spring-tx") // Required for @Transactional in ApplicationService
    // Real k8s liveness/readiness probes need a real health endpoint (2026-07-11,
    // alongside infra/k8s/production/ledger-service.yaml).
    implementation("org.springframework.boot:spring-boot-starter-actuator")
}
