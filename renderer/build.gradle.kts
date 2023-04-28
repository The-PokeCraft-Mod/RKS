fun DependencyHandlerScope.lwjgl(name: String) {
    testImplementation("org.lwjgl:lwjgl-$name")
    testRuntimeOnly("org.lwjgl:lwjgl-" + name + "::${project.properties["lwjglNatives"]}")
}

dependencies {
    implementation(project(path = ":modelLoader"))
    implementation("org.sejda.imageio:webp-imageio:0.1.6")

    lwjgl("glfw")
    lwjgl("stb")
    lwjgl("opengl")
}