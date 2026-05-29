plugins {
    java
    kotlin("jvm") version "2.3.21"
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

tasks.jar {
    exclude("net/minecraft/**")
}
