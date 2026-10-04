plugins { id("com.android.application") }
android {
    namespace = "io.github.andrealtb.artwork.am"
    compileSdk = 35
    defaultConfig {
        applicationId = "io.github.andrealtb.artwork.am"
        minSdk = 30
        targetSdk = 35
        versionCode = 3
        versionName = "0.2.1"
    }
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
    testImplementation("org.json:json:20240303")
}
tasks.configureEach {
    if (name in setOf("assembleRelease", "packageRelease", "bundleRelease", "installRelease")) {
        doFirst { error("AM artwork is a local integration candidate; production signing is not configured.") }
    }
}
