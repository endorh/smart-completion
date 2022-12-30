plugins {
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

val prop = rootProject.extra

val modId: String by rootProject
val modVersion: String by rootProject
val architecturyVersion: String by prop
val minecraftVersion: String by prop
val forgeVersion: String by prop

val modProperties: Map<String, String> by prop

architectury {
    platformSetupLoomIde()
    forge()
}

loom {
    accessWidenerPath.set(project(":common").loom.accessWidenerPath)
    
    forge {
        convertAccessWideners.set(true)
        extraAccessWideners.add(loom.accessWidenerPath.get().asFile.name)
        
        mixinConfig("$modId.mixins.json")
    }
}

val common by configurations.creating
// Don't use shadow from the shadow plugin because we don't want IDEA to index this.
val shadowCommon by configurations.creating
val developmentForge by configurations

configurations {
    compileClasspath.get().extendsFrom(common)
    runtimeClasspath.get().extendsFrom(common)
    developmentForge.extendsFrom(common)
}

dependencies {
    forge("net.minecraftforge:forge:${minecraftVersion}-${forgeVersion}")
    // modApi("dev.architectury:architectury-forge:${architecturyVersion}")
    
    common(project(":common", configuration = "namedElements")) {
        isTransitive = false
    }
    shadowCommon(project(":common", configuration = "transformProductionForge")) {
        isTransitive = false
    }
}

tasks.processResources {
    inputs.properties(modProperties)
    
    exclude("**/.dev/**")
    
    filesMatching("META-INF/mods.toml") {
        expand(modProperties)
    }
}

tasks.shadowJar {
    archiveClassifier.set("dev-shadow")
    
    exclude("fabric.mod.json")
    exclude("architectury.common.json")
    
    configurations = listOf(shadowCommon)
}

tasks.remapJar {
    archiveBaseName.set("$modId-$minecraftVersion-${project.name}")
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
