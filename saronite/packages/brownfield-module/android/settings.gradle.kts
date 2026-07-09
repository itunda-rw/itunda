pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        // React Native has published com.facebook.react:react-android directly
        // to Maven Central since the 0.71 New Architecture rollout.
        mavenCentral()
    }
}

rootProject.name = "saronite-brownfield-module"
