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
        // FrostWire Maven - for jlibtorrent
        maven { url = uri("https://dl.frostwire.com/maven") }
    }
}

rootProject.name = "PureFrame"
include(":app")
