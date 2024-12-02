@file:Suppress("PropertyName")

plugins {
    id("io.github.goooler.shadow") version "8.1.7"
}

val exposed_version = property("deps.exposed_version") as String

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
    sharedLib("org.jetbrains.exposed:exposed-json:$exposed_version")
    sharedLib("org.xerial:sqlite-jdbc:3.46.0.0")
    sharedLib("org.apache.commons:commons-compress:1.26.0")
    // kotlin
    api("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.0.21")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.0")
    api("org.jetbrains.kotlinx:atomicfu:0.26.0")


    api("com.google.guava:guava:31.1-jre")
    api("com.azure:azure-identity:1.10.0")
    api("com.microsoft.graph:microsoft-graph:5.75.0")
}

tasks {
    shadowJar {
        from("LICENSE")

        configurations = listOf(
            project.configurations.shadow.get()
        )
        archiveClassifier.set("all")

        val relocPath = "com.github.zly2006.xbackup."
        relocate("org.jetbrains.exposed", relocPath + "org.jetbrains.exposed")
    }
}
