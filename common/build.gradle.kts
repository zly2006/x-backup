@file:Suppress("PropertyName")

plugins {
    id("io.github.goooler.shadow") version "8.1.7"
}
apply(plugin = "org.jetbrains.kotlin.jvm")
apply(plugin = "org.jetbrains.kotlin.plugin.serialization")

val exposed_version: String by rootProject

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

kotlin {
    jvmToolchain(17)
}

@Suppress("PackageUpdate")
dependencies {
    fun DependencyHandler.sharedLib(dependency: String) =
        shadow(api(dependency)!!)!!

    sharedLib("org.jetbrains.exposed:exposed-core:$exposed_version")
    sharedLib("org.jetbrains.exposed:exposed-jdbc:$exposed_version")
    sharedLib("org.xerial:sqlite-jdbc:3.46.0.0")
    // kotlin
    api("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.0.21")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.0")
    api("org.jetbrains.kotlinx:atomicfu:0.26.0")


    api("com.google.guava:guava:31.1-jre")
    api("com.azure:azure-identity:1.10.4")
    api("com.microsoft.graph:microsoft-graph:5.75.0")
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
}