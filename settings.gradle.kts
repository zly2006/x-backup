pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") {
            name = "Fabric"
        }
        gradlePluginPortal()
    }
}

include("xb-1.21")
include("xb-1.21.2")
include("xb-1.20")
include("xb-1.18")
include("core")
