import java.util.jar.Attributes

plugins {
    java
    kotlin("jvm") version "2.3.21"
    id("com.gradleup.shadow") version "9.0.0"
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
            attributes[Attributes.Name.IMPLEMENTATION_VERSION.toString()] = rootProject.property("mod.version")
            attributes[Attributes.Name.IMPLEMENTATION_TITLE.toString()] = rootProject.property("mod.name")
        }
    }
}
