pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ColorOS-Live-Lyrics-Bridge"

include(":app")
include(":libxposed-api-stubs")
include(":artwork-contract")
include(":artwork-provider-local")
include(":artwork-provider-am")
