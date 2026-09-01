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

// Real, distinct module (2026-09-01) -- see this package's own doc comment (moved
// unchanged from app/src/main/kotlin/rw/itunda/app/security/) for why this had to
// stop being :app-only code: card-service (the first independently-deployable
// product, see docs/ARCHITECTURE.md) needs the exact same JWT-validation filter
// chain :app uses, not a duplicated copy that could silently drift.
dependencies {
    implementation(project(":core"))
    implementation(project(":auth"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("io.mockk:mockk:1.13.12")
}
