// Real static-analysis guardrail for docs/MULTI_AGENT_ISOLATION.md's silo model.
// Plain Kotlin/JVM, not an Android library: Konsist parses .kt source files
// directly off disk via Konsist.scopeFromProject(), so this module needs no
// project(...) dependency on any feature module to see their code.
import org.gradle.api.tasks.testing.Test

plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    testImplementation(libs.konsist)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
