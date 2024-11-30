pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.4.4"
}

rootProject.name = "X Backup"

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
        )
    }
    create(rootProject)
}

include("common")
include("compat-fake-source")
include("cli")
