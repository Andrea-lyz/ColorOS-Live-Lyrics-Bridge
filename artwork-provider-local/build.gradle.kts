plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.andrealtb.artwork.local"
    compileSdk = 35
    defaultConfig {
        applicationId = "io.github.andrealtb.artwork.local"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-local-test"
    }
    // This is an offline protocol fixture, not the Apple Music production provider.
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
}

dependencies {
    implementation(project(":artwork-contract"))
    testImplementation("junit:junit:4.13.2")
}

tasks.configureEach {
    if (name in setOf("assembleRelease", "packageRelease", "bundleRelease", "installRelease")) {
        doFirst { error("The offline artwork fixture is debug-only; no production release is configured.") }
    }
}
