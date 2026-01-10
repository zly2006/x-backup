plugins {
    id("dev.kikugie.stonecutter")
    id("fabric-loom") version "1.13.3" apply false

    kotlin("jvm") version "2.0.21" apply false
    kotlin("plugin.serialization") version "2.0.0" apply false
    id("io.github.goooler.shadow") version "8.1.7" apply false
    base
    id("me.modmuss50.mod-publish-plugin") version "0.8.4" apply false
    id("org.ajoberstar.grgit") version "5.0.0-rc.3"
}
stonecutter active "1.21.5" /* [SC] DO NOT EDIT */

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.serialization")

    repositories {
        mavenLocal()
        mavenCentral()
        maven {
            url = uri("https://maven.shedaniel.me/")
        }
        maven {
            url = uri("https://jitpack.io/")
        }
        maven {
            url = uri("https://masa.dy.fi/maven")
        }
    }

    base {
        archivesName = property("mod.id") as String + "-" + name
    }
}


/*
// Publishes every version
stonecutter registerChiseled tasks.register("chiseledPublishMods", stonecutter.chiseled) {
    group = "project"
    ofTask("publishMods")
}
*/

stonecutter parameters {
    swap("mod_version", "\"${node.project.property("mod.version")}\"")
    swap("git_commit", "\"${grgit.head().abbreviatedId}\"")
    swap("commit_date", "\"${grgit.head().dateTime.toString().substringBefore("[")}\"")
    const("poly_lib", node.project.property("deps.poly_lib").toString().isNotEmpty())
}
