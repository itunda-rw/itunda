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

// Real, fourteenth independently-deployable itunda product (2026-09-06) -- Bank
// product-completeness pass, see docs/ARCHITECTURE.md's dated follow-up. Unlike
// :account/:savings (real reverse Gradle coupling found from commerce/eats/gift/
// merchant/p2p/rideshare's AutoTopUpService and p2p's RoundUpService -- see
// staged-swimming-sun.md's own account of that, a genuine separate decoupling
// decision, not attempted here), :loans was confirmed to have ZERO reverse
// coupling -- only :app declared project(":loans") before this. Runs the exact
// same :loans/:core/:auth code the :app monolith did, in its own JVM, against the
// same MySQL schema -- own bootJar, own Dockerfile, own k8s Deployment, own port,
// so a Loans-only change never needs to compile or redeploy the other product
// modules.
dependencies {
    implementation(project(":loans"))
    implementation(project(":core"))
    implementation(project(":auth"))
    implementation(project(":security"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    implementation("com.mysql:mysql-connector-j")
    // No Flyway dependency at all -- loans-service never migrates the schema, only
    // validates against it (jpa.hibernate.ddl-auto: validate in application.yml);
    // :app remains the sole migration owner of app/src/main/resources/db/migration/.
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
