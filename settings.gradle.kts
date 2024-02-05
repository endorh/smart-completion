import java.util.*

pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://maven.architectury.dev/")
        maven("https://maven.neoforged.net/releases/")
        gradlePluginPortal()
    }
}

include("common")
include("fabric")
include("forge")

rootProject.name = "smartcompletion"
