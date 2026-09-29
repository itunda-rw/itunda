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

// Real, twelfth independently-deployable itunda product (2026-09-01) -- see
// docs/ARCHITECTURE.md's dated follow-up for the full account, following the
// exact pattern the eleven services before it established. Runs the exact
// same :notifications/:core/:auth code as the :app monolith, in its own JVM,
// against the same MySQL schema -- own bootJar, own Dockerfile, own k8s
// Deployment, own port. :notifications (in-app notification list/mark-read,
// push device-token registration, Talk's service-channel feature) was chosen
// as the twelfth extraction because it has zero Gradle-level coupling in
// either direction with any other product module AND zero
// :app-source-level imports of it. Note: the real PushNotificationService
// (used by many other already-extracted services to SEND pushes) lives in
// :core, not this module -- :notifications only owns the REST endpoints for
// listing/marking-read notifications and registering device tokens.
dependencies {
    implementation(project(":notifications"))
    implementation(project(":core"))
    implementation(project(":auth"))
    implementation(project(":security"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    implementation("com.mysql:mysql-connector-j")
    // No Flyway dependency at all -- notifications-service never migrates the
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
