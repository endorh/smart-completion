plugins {

    id("com.github.johnrengelman.shadow") version "7.1.2"
}

val prop = rootProject.extra

val modId: String by rootProject
val modVersion: String by rootProject
val architecturyVersion: String by prop
val minecraftVersion: String by prop
val neoForgeVersion: String by prop
val modsTomlMinecraftVersion: String by prop

val modProperties: Map<String, String> by prop

architectury {
    platformSetupLoomIde()
    neoForge()
}

loom {
    accessWidenerPath.set(project(":common").loom.accessWidenerPath)

    neoForge {
        // mixinConfig("$modId.mixins.json")
    }
}

val common by configurations.creating
// Don't use shadow from the shadow plugin because we don't want IDEA to index this.
val shadowCommon by configurations.creating
val developmentNeoForge by configurations

configurations {
    compileClasspath.get().extendsFrom(common)
    runtimeClasspath.get().extendsFrom(common)
    developmentNeoForge.extendsFrom(common)
}

repositories {
    maven("https://maven.neoforged.net/releases/")
}

dependencies {
    neoForge("net.neoforged:neoforge:$neoForgeVersion")
    // modApi("dev.architectury:architectury-neoforge:$architecturyVersion")
    
    common(project(":common", configuration = "namedElements")) {
        isTransitive = false
    }
    shadowCommon(project(":common", configuration = "transformProductionNeoForge")) {
        isTransitive = false
    }
}

val modsTomlPattern = Regex("""^META-INF/mods\..*\.toml$""", RegexOption.IGNORE_CASE)
tasks.processResources {
    inputs.properties(modProperties)
    
    // Exclude .dev folders from the mod resources
    exclude("**/.dev/**")
    exclude {
        it.relativePath.pathString.matches(modsTomlPattern)
          && it.name.lowercase() != "mods.$modsTomlMinecraftVersion.toml".lowercase()
    }

    filesMatching(listOf("META-INF/mods.toml", "META-INF/mods.$modsTomlMinecraftVersion.toml")) {
        expand(modProperties)
        name = "mods.toml"
    }
}

tasks.shadowJar {
    archiveClassifier.set("dev-shadow")
    
    exclude("fabric.mod.json")
    exclude("architectury.common.json")
    
    configurations = listOf(shadowCommon)
}

tasks.remapJar {
    archiveBaseName.set("$modId-$minecraftVersion-neoforge")
    archiveVersion.set(modVersion)
    archiveClassifier.set("")
    
    inputFile.set(tasks.shadowJar.get().archiveFile)
    
    dependsOn(tasks.shadowJar)
}

tasks.jar {
    archiveClassifier.set("dev")
}

tasks.sourcesJar {
    val commonSources = project(":common").tasks.getByName<Jar>("sourcesJar")
    dependsOn(commonSources)
    
    from(commonSources.archiveFile.map { zipTree(it) })
}

components.getByName<AdhocComponentWithVariants>("java") {
    withVariantsFromConfiguration(configurations.shadowRuntimeElements.get()) {
        skip()
    }
}

publishing {
    publications {
        register<MavenPublication>("forge") {
            artifactId = "$modId-$minecraftVersion-${project.name}"
            version = modVersion
        
            from(components["java"])
        }
    }
}
