plugins {
    id("java")
}

group = "io.github.zly2006"
version = rootProject.version

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    jvmToolchain(8)
}

val exposed_version = property("deps.exposed_version") as String

dependencies {
    api("org.jetbrains.exposed:exposed-core:$exposed_version")
    api("org.jetbrains.exposed:exposed-jdbc:$exposed_version")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}
