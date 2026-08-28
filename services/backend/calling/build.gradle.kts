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
    // For RateLimiter -- real anti-spam limit on call initiation, same convention as
    // every other real-time-abuse-prone endpoint in this codebase.
    implementation(project(":auth"))
    // Deliberately NOT :messaging -- see CallService's own doc comment on the real
    // circular Spring bean dependency that created (MessagingWebSocketHandler needs
    // CallService, MessagingService needs RealtimeMessagePublisher, which
    // MessagingWebSocketHandler implements). CallService depends on
    // ConversationRepository (:core) directly instead.
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.security:spring-security-core")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("io.mockk:mockk:1.13.12")
}
