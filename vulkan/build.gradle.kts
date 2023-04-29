fun DependencyHandlerScope.lwjgl(name: String, useNatives: Boolean = true) {
    implementation("org.lwjgl:lwjgl-$name")
    if (useNatives) runtimeOnly("org.lwjgl:lwjgl-" + name + "::${project.properties["lwjglNatives"]}")
}

dependencies {
    implementation(project(path = ":modelLoader"))
    implementation("org.tinylog:tinylog-api:2.6.1")
    implementation("org.tinylog:tinylog-impl:2.6.1")

    lwjgl("glfw")
    lwjgl("assimp")
    lwjgl("stb")
    lwjgl("vulkan", false)
    lwjgl("shaderc")
    lwjgl("vma")
}