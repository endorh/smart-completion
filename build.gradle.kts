import net.fabricmc.loom.api.LoomGradleExtensionAPI

plugins {
    id("architectury-plugin") version "3.4-SNAPSHOT"
    id("dev.architectury.loom") version "0.12.0-SNAPSHOT" apply false
}

val minecraftVersion: String by project
val parchmentVersion: String by project
val mavenGroup: String by project

val modId: String by project
val modName: String by project
val modVersion: String by project
val modDescription: String by project
val modAuthor: String by project
val modLicense: String by project
val modPage: String by project
val modIssueTracker: String by project
val githubRepo: String by project

val modProperties by extra {
    mapOf(
        "modId" to modId,
        "modName" to modName,
        "version" to modVersion,
        "modDescription" to modDescription.replace("\n", "\\n"),
        "modAuthor" to modAuthor,
        "modLicense" to modLicense,
        "modPage" to modPage,
        "modIssueTracker" to modIssueTracker,
        "githubRepo" to githubRepo,
    )
}

fun sysProp(name: String) = System.getProperty(name)
println(
    "Mod: \"$displayName\" ($modId), version: ${minecraftVersion}-${modVersion}")
println(
    "Java: ${sysProp("java.version")}, " +
    "JVM: ${sysProp("java.vm.version")}(${sysProp("java.vendor")}), " +
    "Arch: ${sysProp("os.arch")}")

architectury {
    minecraft = minecraftVersion
}

subprojects {
    apply(plugin = "dev.architectury.loom")
    
    repositories {
        maven("https://maven.parchmentmc.org") {
            name = "ParchmentMC"
        }
    }
    
    extensions.configure<LoomGradleExtensionAPI> {
        silentMojangMappingsLicense()
        
        dependencies {
            "minecraft"("com.mojang:minecraft:${minecraftVersion}")
            
            "mappings"(layered {
                officialMojangMappings()
                parchment("org.parchmentmc.data:parchment-${minecraftVersion}:${parchmentVersion}@zip")
            })
        }
    }
}

allprojects {
    apply(plugin = "java")
    apply(plugin = "architectury-plugin")
    apply(plugin = "maven-publish")
    
    version = modVersion
    group = mavenGroup
    
    repositories {
    
    }
    
    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(17)
    }
    
    tasks.withType<Test>().all {
        useJUnitPlatform()
    }
    
    extensions.configure<JavaPluginExtension> {
        withSourcesJar()
    }
    
    extensions.configure<PublishingExtension> {
        repositories {
            maven("https://maven.pkg.github.com/$githubRepo") {
                name = "GitHubPackages"
                credentials {
                    username = project.findProperty("gpr.user") as String? ?: System.getenv("USERNAME")
                    password = project.findProperty("gpr.key") as String? ?: System.getenv("TOKEN")
                }
            }
            
            maven(rootProject.projectDir.parentFile.resolve("maven")) {
                name = "LocalMods"
            }
        }
    }
}