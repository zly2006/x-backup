@file:Suppress("PropertyName")

apply(plugin = "fabric-loom")
apply(plugin = "org.jetbrains.kotlin.jvm")
apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
plugins {
    id("io.github.goooler.shadow") version "8.1.7"
}
val exposed_version: String by rootProject

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

loom {
//    splitEnvironmentSourceSets()
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

kotlin {
    jvmToolchain(17)
}

tasks {
    shadowJar {
        from("LICENSE")

        configurations = listOf(
            project.configurations.shadow.get()
        )
        archiveClassifier.set("dev-all")

        exclude("kotlin/**", "kotlinx/**", "javax/**")
        exclude("org/checkerframework/**", "org/intellij/**", "org/jetbrains/annotations/**")
        exclude("com/google/gson/**")
        exclude("org/slf4j/**")

        val relocPath = "com.github.zly2006.xbackup."
        relocate("org.jetbrains.exposed", relocPath + "org.jetbrains.exposed")
    }

    remapJar {
        dependsOn(shadowJar)
        input.set(shadowJar.get().archiveFile)
    }
}

dependencies {
    fun DependencyHandler.shadowImpl(
        dependency: String,
    ): Dependency? {
        return shadow(implementation(dependency)!!)
    }

    shadowImpl("org.jetbrains.exposed:exposed-core:$exposed_version")
//    shadowImpl("org.jetbrains.exposed:exposed-dao:$exposed_version")
    shadowImpl("org.jetbrains.exposed:exposed-jdbc:$exposed_version")
    shadowImpl("org.xerial:sqlite-jdbc:3.46.0.0")

    shadowImpl("com.google.guava:guava:31.1-jre")
    shadowImpl("com.azure:azure-identity:1.10.4")
    shadowImpl("com.microsoft.graph:microsoft-graph:5.75.0")
}
