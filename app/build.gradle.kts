plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.andrealtb.lockscreenlyrics"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.andrealtb.lockscreenlyrics"
        minSdk = 26
        targetSdk = 35
        versionCode = 54
        versionName = "0.18.32"
    }

    buildFeatures {
        buildConfig = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    compileOnly(project(":libxposed-api-stubs"))
}
