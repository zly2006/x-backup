pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.kikugie.dev/releases/")
        maven {
            name = "Kikugie Snapshot"
            url = uri("https://maven.kikugie.dev/snapshots")
        }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.7.7-beta.2"
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
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
            "1.21.9",
            "1.21.11",
        )
    }
    create(rootProject)
}

include("compat-fake-source")
include("cli")
include("api")
