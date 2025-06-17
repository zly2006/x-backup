pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.kikugie.dev/releases/")
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.5.2"
}

rootProject.name = "X Backup"
include("common")

stonecutter {
    kotlinController = true
    centralScript = "build.gradle.kts"

    shared {
        versions(
            "1.20.1",
            "1.20.4",
            "1.20.6",
            "1.21.1",
            "1.21.3",
            "1.21.4",
            "1.21.5",
            "1.21.6",
        )
    }
    create(rootProject)
}

include("compat-fake-source")
include("cli")
include("api")
