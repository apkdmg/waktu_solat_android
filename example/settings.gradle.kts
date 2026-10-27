pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "WaktuSolatAndroidExample"

// Builds the library from the parent folder. A real app would add
// maven("https://jitpack.io") above and drop this line.
includeBuild("..")

include(":app")
