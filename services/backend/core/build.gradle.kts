plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    id("java-library")
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.4")
    }
}

dependencies {
    // api, not implementation: :auth and :wallet depend on :core and need JpaRepository,
    // @Transactional, etc. visible on their own compile classpath, not just core's.
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    api("com.fasterxml.jackson.core:jackson-databind")
    // Real event backbone for the event model docs/TOSS_RWANDA_ALIGNMENT.md already
    // designed (transfer.confirmed, ledger.posted, etc.) -- previously documented but
    // never emitted anywhere from this backend (2026-07-11 fix, see LedgerService.kt).
    api("org.springframework.kafka:spring-kafka")
    implementation("com.mysql:mysql-connector-j")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Toss's own engineering blog (toss.tech/article/test-strategy-server) and multiple
    // Toss-adjacent Kotlin shops document Kotest + MockK, not JUnit+Mockito, as their
    // Kotlin testing convention — idiomatic DSL assertions and mocking instead of
    // fighting Java-oriented tools from Kotlin.
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("io.mockk:mockk:1.13.12")
}
