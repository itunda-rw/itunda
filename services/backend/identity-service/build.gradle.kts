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

// Real, ninth independently-deployable itunda product (2026-09-01) -- see
// docs/ARCHITECTURE.md's dated follow-up for the full account, following the
// exact pattern the eight services before it established. Runs the exact same
// :identity/:core/:auth code as the :app monolith, in its own JVM, against the
// same MySQL schema -- own bootJar, own Dockerfile, own k8s Deployment, own
// port. :identity (real NIDA/KYB identity verification + compliance) was
// chosen as the ninth extraction because it has zero Gradle-level coupling in
// either direction with any other product module AND zero
// :app-source-level imports of it. Its own /api/v1/identity route
// previously had a flagged, now-resolved route-collision hazard with
// :partners' distinct /api/v1/identity/verification sub-path -- see
// services/api-gateway/index.js's own comment on the required registration
// order (this route MUST be registered after partners-service's existing
// /api/v1/identity/verification line).
dependencies {
    implementation(project(":identity"))
    implementation(project(":core"))
    implementation(project(":auth"))
    implementation(project(":security"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    implementation("com.mysql:mysql-connector-j")
    // No Flyway dependency at all -- identity-service never migrates the
    // schema, only validates against it (jpa.hibernate.ddl-auto: validate in
    // application.yml); :app remains the sole migration owner of
    // app/src/main/resources/db/migration/.
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
