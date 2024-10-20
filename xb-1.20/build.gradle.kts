loom {
    accessWidenerPath = file("src/main/resources/xb120.accesswidener")
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
    modRuntimeOnly("carpet:fabric-carpet:1.20.3-1.4.128+v231205")
    modRuntimeOnly("maven.modrinth:carpet-tis-addition:v1.63.0-mc1.20.4")
}
