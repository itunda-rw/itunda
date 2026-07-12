plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    // API controller can call domain logic
    implementation(project(":payment-service:payment-domain"))
    implementation(project(":core-libs"))

    // API should NEVER call DB directly. Toss rule: DB is runtime only at the entrypoint.
    runtimeOnly(project(":payment-service:payment-db"))
    
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.kafka:spring-kafka") // For Outbox CDC or direct API event emission
    // Real k8s liveness/readiness probes need a real health endpoint (2026-07-11,
    // alongside infra/k8s/production/payment-service.yaml).
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
}
