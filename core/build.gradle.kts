@file:Suppress("PropertyName")

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
    api(project(":common"))
    shadow(project(":common", configuration = "shadow"))
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
        exclude("_COROUTINE/**")

        val relocPath = "com.github.zly2006.xbackup."
        relocate("org.jetbrains.exposed", relocPath + "org.jetbrains.exposed")
    }

    remapJar {
        dependsOn(shadowJar)
        input.set(shadowJar.get().archiveFile)
    }
}
