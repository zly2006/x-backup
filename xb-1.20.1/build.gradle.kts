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

val tisAddition = false

if (tisAddition) {
    repositories {
        maven {
            url = uri("https://maven.fallenbreath.me/releases")
        }
    }
}
