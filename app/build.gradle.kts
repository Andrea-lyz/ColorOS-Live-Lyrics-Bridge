plugins {
    id("com.android.application")
}

val releaseStoreFilePath = providers.environmentVariable("SIGNING_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("KEY_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("KEY_PASSWORD").orNull
val hasReleaseSigningConfig = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

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

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = file(releaseStoreFilePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    compileOnly(project(":libxposed-api-stubs"))
}
