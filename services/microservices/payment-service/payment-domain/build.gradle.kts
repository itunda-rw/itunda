plugins {
    kotlin("jvm")
}

dependencies {
    // Toss Rule: Domain module has NO external framework dependencies
    // Pure Kotlin business logic only.
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
