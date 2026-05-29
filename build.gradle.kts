plugins {
    `maven-publish`
    id("net.fabricmc.fabric-loom") version "1.16-SNAPSHOT"
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.serialization") version "2.3.21"
    id("com.gradleup.shadow") version "9.0.0"
    id("me.modmuss50.mod-publish-plugin") version "0.5.1"
}

import org.gradle.api.tasks.bundling.AbstractArchiveTask

class ModData {
    val id = property("mod.id").toString()
    val name = property("mod.name").toString()
    val version = property("mod.version").toString()
    val group = property("mod.group").toString()
}

class ModDependencies {
    operator fun get(name: String) = property("deps.$name").toString()
}

val mod = ModData()
val deps = ModDependencies()
val mcVersion = "26.1.2"
val mcDep = property("mod.mc_dep").toString()

// MC 26.1.2 is unobfuscated — no remapping needed, use shadowJar directly
val jarTaskProvider = tasks.named<AbstractArchiveTask>("shadowJar")

version = "${mod.version}+$mcVersion"
group = mod.group
base { archivesName.set(mod.id) }

loom {
    accessWidenerPath = rootProject.file("src/main/resources/xb.shared.accesswidener")
}

allprojects {
    repositories {
        mavenCentral()
    }
    configurations.all {
        resolutionStrategy {
            force("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            force("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2")
            force("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.10.2")
        }
    }
}

repositories {
    fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
        forRepository { maven(url) { name = alias } }
        filter { groups.forEach(::includeGroup) }
    }
    mavenCentral()
    strictMaven("https://www.cursemaven.com", "CurseForge", "curse.maven")
    strictMaven("https://api.modrinth.com/maven", "Modrinth", "maven.modrinth")
    maven("https://maven.creeperhost.net")
}

dependencies {
    println("CONFIGS: " + configurations.map { it.name })
    fun fapi(vararg modules: String) = modules.forEach {
        implementation(fabricApi.module(it, deps["fabric_api"]))
    }

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.6.10")

    minecraft("com.mojang:minecraft:$mcVersion")
    // MC 26.1.2 is unobfuscated — no Yarn mappings needed
    implementation("net.fabricmc:fabric-loader:${deps["fabric_loader"]}")
    implementation("net.fabricmc:fabric-language-kotlin:${deps["kotlin_loader_version"]}")
    fapi(
        "fabric-lifecycle-events-v1",
        "fabric-resource-loader-v0",
        "fabric-command-api-v2"
    )

    if (deps["poly_lib"].isNotEmpty()) {
        compileOnly("maven.modrinth:polylib:2.0.6") {
            exclude(group = "net.fabricmc.fabric-api")
            exclude(group = "dev.architectury")
            exclude(group = "teamreborn")
        }
    }

    api(project(":common"))
    configurations.create("compileLib")
    add("compileLib", project(":common"))
    add("compileLib", project(":api"))
    add("compileLib", project(":common", configuration = "shadow"))
    compileOnly(project(":compat-fake-source"))
}

loom {
    decompilers {
        get("vineflower").apply {
            options.put("mark-corresponding-synthetics", "1")
        }
    }

    runConfigs.all {
        ideConfigGenerated(true)
        vmArgs("-Dmixin.debug.export=true")
        runDir = "../../run"
    }
}

// MC 26.1.2 requires Java 25
val javaVersion = 25

java {
    withSourcesJar()
    targetCompatibility = JavaVersion.toVersion(javaVersion)
    sourceCompatibility = JavaVersion.toVersion(javaVersion)
}

kotlin {
    jvmToolchain(javaVersion)
}

tasks.processResources {
    inputs.property("id", mod.id)
    inputs.property("name", mod.name)
    inputs.property("version", mod.version)
    inputs.property("mcdep", mcDep)

    val map = mapOf(
        "id" to mod.id,
        "name" to mod.name,
        "version" to mod.version,
        "mcdep" to mcDep
    )

    filesMatching("fabric.mod.json") { expand(map) }

    dependsOn(project(":common").tasks.processResources)
    outputs.upToDateWhen { false }
    doLast {
        copy {
            from(project(":common").tasks.processResources.get().outputs.files)
            into(outputs.files.first())
        }
    }
}

tasks.register<Copy>("buildAndCollect") {
    group = "build"
    from(jarTaskProvider.flatMap { it.archiveFile })
    into(rootProject.layout.buildDirectory.file("libs/${mod.version}"))
    dependsOn("build")
}

tasks {
    shadowJar {
        from("LICENSE")

        configurations = listOf(
            project.configurations.shadow.get(),
            project.configurations["compileLib"]
        )
        archiveClassifier.set("")

        exclude("kotlin/**", "kotlinx/**", "javax/**")
        exclude("org/checkerframework/**", "org/intellij/**", "org/jetbrains/annotations/**")
        exclude("com/google/gson/**")
        exclude("org/slf4j/**")
        exclude("_COROUTINE/**")
        exclude("org/apache/commons/**")
        val sqliteNativeIgnore = listOf(
            "FreeBSD",
            "Linux-Android",
            "Linux/arm",
            "Linux/armv6",
            "Linux/armv7",
            "Linux/ppc64",
            "Windows/armv7"
        )
        sqliteNativeIgnore.forEach {
            exclude("org/sqlite/native/$it/**")
        }

        val relocatePath = "com.github.zly2006.xbackup.libs."
        listOf(
            "org.jetbrains.exposed",
            "org.apache",
            "io.ktor"
        ).forEach {
            relocate(it, relocatePath + it)
        }
    }
}

publishMods {
    file = jarTaskProvider.flatMap { it.archiveFile }
    displayName = "${mod.name} ${mod.version} for $mcVersion"
    version = "${mod.version}+$mcVersion"
    changelog = rootProject.file("CHANGELOG.md").readText()
    type = STABLE
    modLoaders.add("fabric")

    modrinth {
        projectId = property("publish.modrinth").toString()
        accessToken = providers.environmentVariable("MODRINTH_TOKEN")
        minecraftVersions.add(mcVersion)
        requires("fabric-api", "fabric-language-kotlin")
        optional("polylib")
    }
}

tasks.jar {
    enabled = false
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

