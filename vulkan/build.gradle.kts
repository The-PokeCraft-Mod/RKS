 fun DependencyHandlerScope.lwjgl(name: String, useNatives: Boolean = true) {
    implementation("org.lwjgl:lwjgl-$name")
    if (useNatives) runtimeOnly("org.lwjgl:lwjgl-" + name + "::${project.properties["lwjglNatives"]}")
}

dependencies {
    implementation(project(path = ":modelLoader"))

    lwjgl("glfw")
    lwjgl("assimp")
    lwjgl("stb")
    lwjgl("vulkan", false)
    lwjgl("shaderc")
    lwjgl("vma")
}