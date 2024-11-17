loom {
    accessWidenerPath = file("src/main/resources/xb121.accesswidener")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    modRuntimeOnly("carpet:fabric-carpet:1.21-1.4.147+v240613")
    compileOnly(project(":compat-fake-source", configuration = "namedElements"))
}
