plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
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
    implementation(project(":agents"))
    implementation(project(":wallet"))
    implementation(project(":bills"))
    implementation(project(":loans"))
    implementation(project(":contacts"))
    implementation(project(":stocks"))
    implementation(project(":savings"))
    implementation(project(":notifications"))
    implementation(project(":discover"))
    implementation(project(":insurance"))
    implementation(project(":system"))
    implementation(project(":merchant"))
    implementation(project(":identity"))
    implementation(project(":partners"))
    implementation(project(":certificate"))
    implementation(project(":rewards"))
    implementation(project(":creditscore"))
    implementation(project(":trustscore"))
    implementation(project(":overview"))
    implementation(project(":p2p"))
    implementation(project(":offline"))
    implementation(project(":support"))
    implementation(project(":messaging"))
    implementation(project(":marketplace"))
    implementation(project(":community"))
    implementation(project(":jobs"))
    implementation(project(":realestate"))
    implementation(project(":commerce"))
    implementation(project(":eats"))
    implementation(project(":maps"))
    implementation(project(":gift"))
    implementation(project(":rideshare"))
    implementation(project(":splitbill"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // Real live-transport for messaging (2026-07-18) -- see
    // rw.itunda.app.websocket.MessagingWebSocketHandler's own doc comment.
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    // Real k8s liveness/readiness probes need a real health endpoint -- added
    // 2026-07-11 alongside infra/k8s/production/backend.yaml, which would
    // otherwise crash-loop every pod probing a path that didn't exist.
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // Real Prometheus scrape target -- see infra/k8s/monitoring/prometheus-config.yaml's
    // scrape_configs (2026-07-11 fix, closing the "bare Prometheus Deployment with no
    // scrape config, no ServiceMonitor, no actual metrics wiring" gap).
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    implementation("com.mysql:mysql-connector-j")
    // Real versioned migrations instead of Hibernate ddl-auto inferring the schema --
    // see application.yml's jpa.hibernate.ddl-auto comment (2026-07-11 fix).
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")
    // Without these, Jackson can't deserialize Kotlin data class request bodies at all
    // (LoginRequest, RegisterRequest, etc. all have no default constructor) and every
    // POST endpoint 500s internally before Spring MVC even reaches the controller.
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
}

tasks.named<Jar>("jar") {
    enabled = false
}
