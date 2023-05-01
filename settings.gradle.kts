rootProject.name = "RKS"

pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
    }
}

include("modelLoader")
include("vulkan")
include("tests")