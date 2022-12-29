plugins {
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

val prop = rootProject.extra

val modId: String by rootProject
val modVersion: String by rootProject
val minecraftVersion: String by prop
val fabricLoaderVersion: String by prop
val fabricApiVersion: String by prop
val architecturyVersion: String by prop

val modProperties: Map<String, String> by prop

architectury {
    platformSetupLoomIde()
    fabric()
}

loom {
    accessWidenerPath.set(project(":common").loom.accessWidenerPath)
}

val common by configurations.creating
// Don't use shadow from the shadow plugin because we don't want IDEA to index this.
val shadowCommon by configurations.creating
val developmentFabric by configurations

configurations {
    compileClasspath.get().extendsFrom(common)
    runtimeClasspath.get().extendsFrom(common)
    developmentFabric.extendsFrom(common)
}

dependencies {
    modImplementation("net.fabricmc:fabric-loader:${fabricLoaderVersion}")
    modApi("net.fabricmc.fabric-api:fabric-api:${fabricApiVersion}")
    // modApi("dev.architectury:architectury-fabric:${architecturyVersion}")
    
    common(project(":common", configuration = "namedElements")) {
        isTransitive = false
    }
    shadowCommon(project(":common", configuration = "transformProductionFabric")) {
        isTransitive = false
    }
}

tasks.processResources {
    inputs.properties(modProperties)
    
    exclude("**/.dev/**")
    
    filesMatching("fabric.mod.json") {
        println(modProperties)
        expand(modProperties)
    }
}

tasks.shadowJar {
    archiveClassifier.set("dev-shadow")
    
    exclude("architectury.common.json")
    
    configurations = listOf(shadowCommon)
}

tasks.remapJar {
    archiveBaseName.set("$modId-$minecraftVersion-${project.name}")
    archiveVersion.set(modVersion)
    archiveClassifier.set("")
    
    injectAccessWidener.set(true)
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
        register<MavenPublication>("fabric") {
            artifactId = "$modId-$minecraftVersion-${project.name}"
            version = modVersion
        
            from(components["java"])
        }
    }
}
