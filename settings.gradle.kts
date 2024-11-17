pluginManagement {
    repositories {
        mavenLocal()
        maven("https://maven.fabricmc.net/") {
            name = "Fabric"
        }
        gradlePluginPortal()
    }
}

include("common")
include("compat-fake-source")
include("core")
include("cli")
include("xb-1.21")
include("xb-1.21.2")
include("xb-1.20")
include("xb-1.20.1")
include("xb-1.18")
include("onedrive-support")
