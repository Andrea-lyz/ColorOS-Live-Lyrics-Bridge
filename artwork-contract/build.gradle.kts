plugins {
    id("com.android.library")
}

android {
    namespace = "io.github.andrealtb.artwork.contract"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    buildFeatures { aidl = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
}

dependencies { testImplementation("junit:junit:4.13.2") }
