plugins {
    id("com.android.application") version "8.13.2" apply false
    id("com.android.library") version "8.13.2" apply false
}

apply(from = rootProject.file("gradle/build-identity.gradle.kts"))
