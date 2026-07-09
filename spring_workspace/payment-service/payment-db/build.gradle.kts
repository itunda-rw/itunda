plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa") version "1.9.22"
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    // Depends on the domain for interfaces and entities
    implementation(project(":payment-service:payment-domain"))
    implementation(project(":spring-core-libs"))
    
    // DB specific dependencies (JPA, MySQL, Outbox pattern)
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("com.mysql:mysql-connector-j")
}
