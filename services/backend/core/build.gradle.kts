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
    // RestClient only (not spring-boot-starter-web) -- MtnMomoProviderConnector needs a
    // real synchronous HTTP client to call MTN's real sandbox API, not an embedded
    // servlet container, which this library module has no business pulling in.
    implementation("org.springframework:spring-web")
    // compileOnly, not implementation -- RequestCorrelationFilter needs the servlet API
    // types to compile (OncePerRequestFilter/HttpServletRequest/HttpServletResponse),
    // but :core itself never embeds a servlet container; every real consumer (:app and
    // all 14 extracted *-service modules) already provides the real jakarta.servlet-api
    // jar at runtime via its own spring-boot-starter-web.
    compileOnly("jakarta.servlet:jakarta.servlet-api")
    implementation("com.mysql:mysql-connector-j")
    // Real FCM Admin SDK (see push/RealFcmPushSender.kt's own doc comment) -- the
    // real Google-published client for sending to real device tokens over HTTP v1,
    // same "real SDK, credential is the only simulated boundary" shape as every
    // other external integration in this backend.
    implementation("com.google.firebase:firebase-admin:9.4.1")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Test-only counterpart to the compileOnly servlet-api dependency above --
    // RequestCorrelationFilterTest exercises the filter directly with
    // MockHttpServletRequest/MockHttpServletResponse, which need the real servlet API
    // types on the test compile classpath.
    testImplementation("jakarta.servlet:jakarta.servlet-api")
    // Toss's own engineering blog (toss.tech/article/test-strategy-server) and multiple
    // Toss-adjacent Kotlin shops document Kotest + MockK, not JUnit+Mockito, as their
    // Kotlin testing convention — idiomatic DSL assertions and mocking instead of
    // fighting Java-oriented tools from Kotlin.
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("io.mockk:mockk:1.13.12")
}
