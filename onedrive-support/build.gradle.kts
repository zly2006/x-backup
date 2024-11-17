apply(plugin = "fabric-loom")
apply(plugin = "org.jetbrains.kotlin.jvm")
apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
plugins {
    id("io.github.goooler.shadow") version "8.1.7"
}

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

dependencies {
    api(project(":core", configuration = "namedElements"))
    shadow("com.google.guava:guava:31.1-jre")
    shadow("com.azure:azure-identity:1.10.4")
    shadow("com.microsoft.graph:microsoft-graph:5.75.0")
}

tasks.remapJar {
    dependsOn("shadowJar")
}

tasks.shadowJar {
    isZip64 = true

    configurations = listOf(
        project.configurations.shadow.get()
    )
}