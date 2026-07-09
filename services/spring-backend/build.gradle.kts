import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")                        version "1.9.25" apply false
    kotlin("plugin.spring")              version "1.9.25" apply false
    kotlin("plugin.jpa")                 version "1.9.25" apply false
    id("org.springframework.boot")       version "3.3.4"  apply false
    id("io.spring.dependency-management") version "1.1.6" apply false
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    repositories {
        mavenCentral()
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs += "-Xjsr305=strict"
            jvmTarget = "17"
        }
    }

    tasks.withType<Test> { useJUnitPlatform() }

    dependencies {
        val implementation by configurations
        val testImplementation by configurations
        implementation(kotlin("stdlib"))
        implementation(kotlin("reflect"))
        testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    }
}
