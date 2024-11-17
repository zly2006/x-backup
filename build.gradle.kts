import net.fabricmc.loom.task.GenerateSourcesTask

plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.0"
    id("fabric-loom") version
            if (System.getenv("GITHUB_ACTIONS") == "true")
                "1.8.9"
            else
                "1.8.local"
    id("maven-publish")
    id("io.github.goooler.shadow") version "8.1.7"
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String
val exposed_version: String by project

allprojects {
    apply(plugin = "fabric-loom")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.serialization")

    base {
        if (project != rootProject ) {
            version = rootProject.version
            archivesName.set(project.property("archives_base_name") as String + "-" + project.name)
        }
    }

    repositories {
        maven {
            url = uri("https://api.modrinth.com/maven")
            content {
                includeGroup("maven.modrinth")
            }
        }
        maven {
            url = uri("https://cursemaven.com")
            content {
                includeGroup("curse.maven")
            }
        }

        maven {
            url = uri("https://maven.terraformersmc.com/releases")
        }
        maven {
            url = uri("https://maven.shedaniel.me/")
        }
        maven {
            url = uri("https://jitpack.io/")
        }
        maven {
            url = uri("https://masa.dy.fi/maven")
        }
        mavenCentral()
    }

    dependencies {
        if (project.name.startsWith("xb-")) {
            // compatibility subprojects
            api(project(":core", configuration = "namedElements"))
        }
        modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
        modImplementation("net.fabricmc:fabric-language-kotlin:${project.property("kotlin_loader_version")}")

        minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
        mappings("net.fabricmc:yarn:${project.property("yarn_mappings")}:v2")
        if (project.hasProperty("fabric_version")) {
            modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")
        }
    }

    loom {
        runs {
            named("client") {
                programArg("--username")
                programArg("Dev")
            }
        }
    }


    tasks {
        processResources {
//            inputs.property("version", rootProject.version)
//            inputs.property("minecraft_version", project.property("minecraft_version"))
//            inputs.property("loader_version", project.property("loader_version"))
            filteringCharset = "UTF-8"

            val isRoot = project == rootProject
            val mcVer = project.property("minecraft_version").toString()
            val fabricLoaderVer = project.property("loader_version")
            val kotlinLoaderVer = project.property("kotlin_loader_version")
            println("processResources, project: ${project.name} ${if (isRoot) "(root)" else ""}, mc: $mcVer")

            filesMatching("fabric.mod.json") {
                expand(
                    "version" to rootProject.version,
                    "mc" to mcVer.replace(".", "_"),
                    "minecraft_version" to mcVer,
                    "loader_version" to fabricLoaderVer,
                    "kotlin_loader_version" to kotlinLoaderVer,
                    "build_time" to System.currentTimeMillis(),
                )
            }
        }

        withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
        }

        jar {
            from(rootProject.file("LICENSE"))
        }
    }

    java {
        withSourcesJar()
    }

}

tasks.withType<GenerateSourcesTask> {
}

subprojects {
    configurations {
        modRuntimeOnly {
            exclude(module = "fabric-api-base")
        }
    }
}

repositories {
    // Add repositories to retrieve artifacts from in here.
    // You should only use this when depending on other mods because
    // Loom adds the essential maven repositories to download Minecraft and libraries from automatically.
    // See https://docs.gradle.org/current/userguide/declaring_repositories.html
    // for more information about repositories.
}

dependencies {
    include(project(":core"))
    implementation(project(":core", configuration = "namedElements"))
    implementation(project(":core", configuration = "shadow"))
    subprojects.filter { it.name.startsWith("xb-") }.forEach {
        include(it)
        implementation(project(":" + it.name, configuration = "namedElements"))
    }


    implementation("org.jetbrains.exposed:exposed-core:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed_version")
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")
}

tasks {
    processResources {
//        from("${rootDir}/assets/icon.png") {
//            into("assets/bettersleeping/")
//        }
    }
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(17)
    // Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
    // if it is present.
    // If you remove this line, sources will not be generated.
    withSourcesJar()
}

kotlin {
    jvmToolchain(17)
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName}" }
    }
}

// configure the maven publication
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.property("archives_base_name") as String
            from(components["java"])
        }
    }

    // See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
    repositories {
        // Add repositories to publish to here.
        // Notice: This block does NOT have the same function as the block in the top level.
        // The repositories here will be used for publishing your artifact, not for
        // retrieving dependencies.
    }
}
