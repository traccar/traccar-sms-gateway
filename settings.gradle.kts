pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://maven.scijava.org/content/repositories/public") {
            content {
                includeModule("com.github.duolingo", "rtl-viewpager")
            }
        }
        maven("https://jitpack.io")
    }
}
include(":app")
