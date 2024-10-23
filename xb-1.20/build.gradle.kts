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

    dependencies {
        modImplementation("maven.modrinth:carpet:1.4.128")
        modImplementation("maven.modrinth:carpet-tis-addition:v1.63.0-mc1.20.4")
        modImplementation("maven.modrinth:lithium:mc1.20.4-0.12.1")
        modImplementation("me.fallenbreath:conditional-mixin-fabric:0.6.3")
    }
}
