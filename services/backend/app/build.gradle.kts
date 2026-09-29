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
    // Real, distinct module (2026-09-01) -- see security/build.gradle.kts's own doc
    // comment: SecurityConfig/JwtAuthenticationFilter/DeviceVerificationFilter moved
    // out of this module so card-service shares the identical filter chain.
    implementation(project(":security"))
    // :agents removed (2026-09-01) -- extracted into its own
    // independently-deployable agents-service (see docs/ARCHITECTURE.md), the
    // third product after :card/:insurance. Confirmed via repo-wide grep:
    // nothing else in :app reaches into rw.itunda.agents directly.
    implementation(project(":account"))
    // :bills removed (2026-09-01) -- extracted into its own
    // independently-deployable bills-service, the sixth product after
    // :card/:insurance/:agents/:transit/:certificate. Confirmed via repo-wide
    // grep: nothing else in :app reaches into rw.itunda.bills directly.
    // :loans removed (2026-09-06) -- extracted into its own
    // independently-deployable loans-service, the fourteenth product after
    // :card/:insurance/:agents/:transit/:certificate/:bills/:vehicle/:partners/
    // :identity/:overview/:knowledge/:notifications/:analytics. Confirmed via
    // repo-wide grep: nothing else in :app (or any other module) declares
    // project(":loans") or reaches into rw.itunda.loans directly.
    implementation(project(":contacts"))
    implementation(project(":stocks"))
    implementation(project(":savings"))
    // :notifications removed (2026-09-01) -- extracted into its own
    // independently-deployable notifications-service, the twelfth product
    // after :card/:insurance/:agents/:transit/:certificate/:bills/:vehicle/
    // :partners/:identity/:overview/:knowledge. Confirmed via repo-wide grep:
    // nothing else in :app reaches into rw.itunda.notifications directly.
    implementation(project(":discover"))
    // :analytics removed (2026-09-02) -- extracted into its own
    // independently-deployable analytics-service, the thirteenth product
    // after :card/:insurance/:agents/:transit/:certificate/:bills/:vehicle/
    // :partners/:identity/:overview/:knowledge/:notifications. Confirmed via
    // repo-wide grep: nothing else in :app reaches into rw.itunda.analytics
    // directly.
    // :insurance removed (2026-09-01) -- extracted into its own
    // independently-deployable insurance-service (see docs/ARCHITECTURE.md), the
    // second product after :card. Confirmed via repo-wide grep: nothing else in
    // :app reaches into rw.itunda.insurance directly.
    implementation(project(":system"))
    implementation(project(":merchant"))
    // :identity removed (2026-09-01) -- extracted into its own
    // independently-deployable identity-service, the ninth product after
    // :card/:insurance/:agents/:transit/:certificate/:bills/:vehicle/:partners.
    // Confirmed via repo-wide grep: nothing else in :app reaches into
    // rw.itunda.identity directly.
    // :partners removed (2026-09-01) -- extracted into its own
    // independently-deployable partners-service, the eighth product after
    // :card/:insurance/:agents/:transit/:certificate/:bills/:vehicle.
    // Confirmed via repo-wide grep: nothing else in :app reaches into
    // rw.itunda.partners directly.
    // :certificate removed (2026-09-01) -- extracted into its own
    // independently-deployable certificate-service, the fifth product after
    // :card/:insurance/:agents/:transit. Confirmed via repo-wide grep: nothing
    // else in :app reaches into rw.itunda.certificate directly.
    implementation(project(":rewards"))
    implementation(project(":creditscore"))
    implementation(project(":trustscore"))
    // :overview removed (2026-09-01) -- extracted into its own
    // independently-deployable overview-service, the tenth product after
    // :card/:insurance/:agents/:transit/:certificate/:bills/:vehicle/
    // :partners/:identity. Confirmed via repo-wide grep: nothing else in
    // :app reaches into rw.itunda.overview directly.
    implementation(project(":p2p"))
    implementation(project(":offline"))
    implementation(project(":support"))
    implementation(project(":messaging"))
    implementation(project(":marketplace"))
    implementation(project(":community"))
    // :knowledge removed (2026-09-01) -- extracted into its own
    // independently-deployable knowledge-service, the eleventh product after
    // :card/:insurance/:agents/:transit/:certificate/:bills/:vehicle/
    // :partners/:identity/:overview. Confirmed via repo-wide grep: nothing
    // else in :app reaches into rw.itunda.knowledge directly.
    implementation(project(":jobs"))
    implementation(project(":realestate"))
    implementation(project(":commerce"))
    implementation(project(":eats"))
    implementation(project(":maps"))
    implementation(project(":gift"))
    implementation(project(":calling"))
    implementation(project(":rideshare"))
    // :vehicle removed (2026-09-01) -- extracted into its own
    // independently-deployable vehicle-service, the seventh product after
    // :card/:insurance/:agents/:transit/:certificate/:bills. Confirmed via
    // repo-wide grep: nothing else in :app reaches into rw.itunda.vehicle
    // directly.
    implementation(project(":family"))
    implementation(project(":splitbill"))
    // :card removed (2026-09-01) -- extracted into its own independently-deployable
    // card-service (see docs/ARCHITECTURE.md); this is the change that actually
    // shrinks :app:bootJar's compile graph for a Card-only change. Confirmed via
    // repo-wide grep: nothing else in :app reaches into rw.itunda.card directly.
    // :transit removed (2026-09-01) -- extracted into its own
    // independently-deployable transit-service, the fourth product after
    // :card/:insurance/:agents. Confirmed via repo-wide grep: nothing else in
    // :app reaches into rw.itunda.transit directly.
    implementation(project(":ussd"))
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
