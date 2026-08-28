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
    // For MessagingService.getConversationForParticipant -- a call is always scoped to
    // a real, existing 1:1 Conversation between exactly two real participants, reusing
    // that IDOR check rather than inventing a second one.
    implementation(project(":messaging"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.security:spring-security-core")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("io.mockk:mockk:1.13.12")
}
