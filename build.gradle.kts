plugins {
    id("com.android.application") version "8.13.2" apply false
    id("com.android.library") version "8.13.2" apply false
    // Kotlin/Compose are for the ordinary-App management UI only (5.0 plan §8.0/§8.4).
    // Same Kotlin version as the Providers repository; AGP stays 8.13.2 (no AGP 9 migration).
    id("org.jetbrains.kotlin.android") version "2.3.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20" apply false
}

apply(from = rootProject.file("gradle/build-identity.gradle.kts"))
