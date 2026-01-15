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
        maven { url = java.net.URI("https://jitpack.io") }
        maven { url = java.net.URI("https://guardianproject.info/releases/") }
        maven { url = java.net.URI("https://raw.githubusercontent.com/guardianproject/gpmaven/master/") }
    }
}

rootProject.name = "Anomes"
include(":app")
