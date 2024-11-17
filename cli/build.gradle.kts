plugins {
    id("io.github.goooler.shadow") version "8.1.7"
}
apply(plugin = "org.jetbrains.kotlin.jvm")
apply(plugin = "org.jetbrains.kotlin.plugin.serialization")

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
    fun DependencyHandler.shadowLib(dependency: Any) =
        shadow(api(dependency)!!)!!

    shadowLib(project(":common"))
}

tasks {
    shadowJar {
        from("LICENSE")

        configurations = listOf(
            project.configurations.shadow.get()
        )
        archiveClassifier.set("all")
        manifest {
            attributes["Main-Class"] = "Main"
        }

        val relocPath = "com.github.zly2006.xbackup."
        relocate("org.jetbrains.exposed", relocPath + "org.jetbrains.exposed")
    }
}
