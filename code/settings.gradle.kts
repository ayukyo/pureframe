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
        // JitPack - for jlibtorrent
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "PureFrame"
include(":app")
