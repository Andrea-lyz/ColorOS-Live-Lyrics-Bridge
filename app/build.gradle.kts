plugins {
    id("com.android.application")
}

val releaseStoreFilePath = providers.environmentVariable("SIGNING_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("KEY_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("KEY_PASSWORD").orNull
val defaultVersionName = "1.8.0"
val releaseVersionName = providers.gradleProperty("releaseTag")
    .orElse(providers.environmentVariable("RELEASE_TAG"))
    .map { tag ->
        val normalizedTag = tag.trim().removePrefix("refs/tags/")
        val withoutLeadingV = if (
            normalizedTag.length > 1 &&
            (normalizedTag[0] == 'v' || normalizedTag[0] == 'V') &&
            normalizedTag[1].isDigit()
        ) {
            normalizedTag.substring(1)
        } else {
            normalizedTag
        }
        withoutLeadingV.ifBlank { defaultVersionName }
    }
    .orElse(defaultVersionName)
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
        versionCode = 80
        versionName = releaseVersionName.get()
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
    implementation("com.mocharealm.accompanist:lyrics-core-jvm:0.4.5")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.0")
    implementation("org.luckypray:dexkit:2.2.0")
    testImplementation("junit:junit:4.13.2")
}
