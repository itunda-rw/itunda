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
    implementation(project(":wallet"))
    implementation(project(":bills"))
    implementation(project(":loans"))
    implementation(project(":contacts"))
    implementation(project(":stocks"))
    implementation(project(":savings"))
    implementation(project(":notifications"))
    implementation(project(":discover"))
    implementation(project(":insurance"))
    implementation(project(":system"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("com.mysql:mysql-connector-j")
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
