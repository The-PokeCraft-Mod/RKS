plugins {
    id("java")
    id("maven-publish")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "maven-publish")
    apply(plugin = "com.github.johnrengelman.shadow")

    version = "1.2.3"
    group = "com.thepokecraftmod"
    val lwjglVersion = project.properties["lwjglVersion"]
    val jomlVersion = project.properties["jomlVersion"]
    val lwjglNatives = project.properties["lwjglNatives"]

    configurations {
        implementation.get().extendsFrom(configurations.shadow.get())
    }

    repositories {
        mavenCentral()
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        maven("https://maven.thepokecraftmod.com/releases")
        maven("https://jitpack.io")
    }

    dependencies {
        implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))
        implementation("org.joml:joml:${jomlVersion}")
        implementation("org.lwjgl:lwjgl")

        implementation("org.jetbrains:annotations:24.0.1")
        implementation("org.slf4j:slf4j-api:2.0.7")
        implementation("com.thebombzen:jxlatte:1.1.0")
        implementation("com.google.flatbuffers:flatbuffers-java:23.3.3")

        runtimeOnly("org.slf4j:slf4j-simple:2.0.7")
        runtimeOnly("org.lwjgl:lwjgl::$lwjglNatives")
    }

    publishing {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
            }
        }

        repositories {
            mavenLocal()
            maven {
                name = "PokeCraft"
                url = uri("https://maven.thepokecraftmod.com/releases")
                credentials {
                    username = project.properties["pokecraftLogin"]?.toString() ?: findProperty("REPO_LOGIN").toString()
                    password = project.properties["pokecraftToken"]?.toString() ?: findProperty("REPO_PASSWORD").toString()
                }
            }

        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }

    tasks.build {
        dependsOn(tasks.shadowJar)
    }

    java {
        withSourcesJar()

        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }
}
