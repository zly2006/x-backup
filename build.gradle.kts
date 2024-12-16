plugins {
    `maven-publish`
    id("fabric-loom")
     kotlin("jvm")
    kotlin("plugin.serialization")
    id("io.github.goooler.shadow") version "8.1.7"
    id("dev.kikugie.j52j")
    id("me.modmuss50.mod-publish-plugin")
}

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
val mcVersion = stonecutter.current.version
val mcDep = property("mod.mc_dep").toString()

version = "${mod.version}+$mcVersion"
group = mod.group
base { archivesName.set(mod.id) }

loom {
    accessWidenerPath = rootProject.file("src/main/resources/xb.shared.accesswidener")
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
    fun fapi(vararg modules: String) = modules.forEach {
        modImplementation(fabricApi.module(it, deps["fabric_api"]))
    }

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.6.10")

    minecraft("com.mojang:minecraft:$mcVersion")
    mappings("net.fabricmc:yarn:$mcVersion+build.${deps["yarn_build"]}:v2")
    modImplementation("net.fabricmc:fabric-loader:${deps["fabric_loader"]}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${deps["kotlin_loader_version"]}")
    fapi(
        // Add modules from https://github.com/FabricMC/fabric
        "fabric-lifecycle-events-v1",
        "fabric-resource-loader-v0"
    )

    if (stonecutter.eval(stonecutter.current.version, ">=1.20")) {
        fapi("fabric-command-api-v2")
    }

    if (deps["poly_lib"].isNotEmpty()) {
        modCompileOnly("net.creeperhost:polylib-fabric:${deps["poly_lib"]}") {
            exclude(group = "net.fabricmc.fabric-api")
            exclude(group = "dev.architectury")
            exclude(group = "teamreborn")
        }
    }

    api(project(":common"))
    configurations.create("compileLib") {
        defaultDependencies {
            add(project(":common", configuration = "shadow"))
        }
    }
    compileOnly(project(":compat-fake-source"))
}

loom {
    decompilers {
        get("vineflower").apply { // Adds names to lambdas - useful for mixins
            options.put("mark-corresponding-synthetics", "1")
        }
    }

    runConfigs.all {
        ideConfigGenerated(true)
        vmArgs("-Dmixin.debug.export=true")
        runDir = "../../run"
    }
}

val javaVersion =
    if (stonecutter.eval(mcVersion, ">=1.20.6")) 21
    else 17

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
        // copying this is for dev only, int here is a shadowJar task
        copy {
            from(project(":common").tasks.processResources.get().outputs.files)
            into(outputs.files.first())
        }
    }
}

tasks.register<Copy>("buildAndCollect") {
    group = "build"
    from(tasks.remapJar.get().archiveFile)
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
        archiveClassifier.set("dev-all")

        exclude("kotlin/**", "kotlinx/**", "javax/**")
        exclude("org/checkerframework/**", "org/intellij/**", "org/jetbrains/annotations/**")
        exclude("com/google/gson/**")
        exclude("org/slf4j/**")
        exclude("_COROUTINE/**")
        exclude("org/apache/commons/**")
        val sqliteNativeIgnore = listOf("FreeBSD", "Linux-Android")
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

    remapJar {
        dependsOn(shadowJar)
        input.set(shadowJar.get().archiveFile)
    }
}


publishMods {
    file = tasks.remapJar.get().archiveFile
    displayName = "${mod.name} ${mod.version} for $mcVersion"
    version = "${mod.version}+$mcVersion"
    changelog = rootProject.file("CHANGELOG.md").readText()
    type = STABLE
    modLoaders.add("fabric")

//    dryRun = providers.environmentVariable("MODRINTH_TOKEN")
//        .getOrNull() == null || providers.environmentVariable("CURSEFORGE_TOKEN").getOrNull() == null

    modrinth {
        projectId = property("publish.modrinth").toString()
        accessToken = providers.environmentVariable("MODRINTH_TOKEN")
        minecraftVersions.addAll(
            property("mod.mc_targets").toString().split(" ")
                .filter { it.isNotBlank() }
                .plus(mcVersion)
                .distinct()
        )
        requires("fabric-api", "fabric-language-kotlin")
        optional("polylib")
    }

    curseforge {
        projectId = property("publish.curseforge").toString()
        accessToken = providers.environmentVariable("CURSEFORGE_TOKEN")
        minecraftVersions.addAll(
            property("mod.mc_targets").toString().split(" ")
                .filter { it.isNotBlank() }
                .plus(mcVersion)
                .distinct()
        )
        requires("fabric-api", "fabric-language-kotlin")
        optional("polylib")
    }
}

/*
publishing {
    repositories {
        maven("...") {
            name = "..."
            credentials(PasswordCredentials::class.java)
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }

    publications {
        create<MavenPublication>("mavenJava") {
            groupId = "${property("mod.group")}.${mod.id}"
            artifactId = mod.version
            version = mcVersion

            from(components["java"])
        }
    }
}
*/
