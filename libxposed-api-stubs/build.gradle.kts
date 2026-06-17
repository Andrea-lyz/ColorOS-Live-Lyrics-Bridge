plugins {
    id("com.android.library")
}

android {
    namespace = "io.github.libxposed.api.stubs"
    compileSdk = 35
    androidResources.enable = false

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        buildConfig = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
