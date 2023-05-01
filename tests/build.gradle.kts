plugins {
    id("fabric-loom") version "1.2-SNAPSHOT"
    id("maven-publish")
}

val minecraftVersion = project.properties["minecraft_version"]
val parchmentVersion = project.properties["parchment_version"]
val loaderVersion = project.properties["loader_version"]

fun DependencyHandlerScope.lwjgl(name: String, useNatives: Boolean = true) {
    implementation("org.lwjgl:lwjgl-$name")
    if (useNatives) runtimeOnly("org.lwjgl:lwjgl-" + name + "::${project.properties["lwjglNatives"]}")
}

repositories {
    maven("https://maven.parchmentmc.org")
    maven("https://maven.thepokecraftmod.com/releases")
}

dependencies {
    implementation(project(path = ":modelLoader"))
    implementation(project(path = ":vulkan"))

    minecraft("com.mojang:minecraft:${minecraftVersion}")
    mappings(loom.layered {
        officialMojangMappings()
        parchment("org.parchmentmc.data:parchment-$parchmentVersion@zip")
    })
    modImplementation("net.fabricmc:fabric-loader:${loaderVersion}")

    lwjgl("assimp")
    lwjgl("vulkan", false)
    lwjgl("shaderc")
    lwjgl("vma")
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", minecraftVersion)
    inputs.property("loader_version", loaderVersion)
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand(mapOf(
                "version" to project.version,
                "minecraft_version" to minecraftVersion,
                "loader_version" to loaderVersion
        ))
    }
}