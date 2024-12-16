import java.util.jar.Attributes

plugins {
    id("io.github.goooler.shadow") version "8.1.7"
}

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.guardsquare:proguard-gradle:7.6.0")
    }
}

apply(plugin = "java")

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

tasks.create<proguard.gradle.ProGuardTask>("proguard") {
    configuration(file("proguard.pro"))
    injars(tasks.shadowJar.get().outputs)
    ignorewarnings()
    outjars("build/libs/xbackup-all-proguard.jar")
}
