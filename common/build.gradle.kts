plugins {
    id("net.fabricmc.fabric-loom")
    id("maven-publish")
}

val prop = rootProject.extra

val modId: String by rootProject
val minecraftVersion: String by prop
val fabricLoaderVersion: String by prop

val modProperties: Map<String, String> by prop

loom {
    // TODO: Add support for split env source sets
    // splitEnvironmentSourceSets()
}

dependencies {
    // To change the versions see the gradle.properties file
    minecraft("com.mojang:minecraft:${minecraftVersion}")

    // TODO: Exclude fabric launcher API from common source set.
    //       We add it as a dependency to get mixin, which we could probably refer directly,
    //       but that would require listing a specific version, which requires more maintenance.
    //       Unfortunately, there's no easy way to import the dependencies of a module without
    //       the module itself, and we cannot exclude specific packages from a dependency.
    implementation("net.fabricmc:fabric-loader:${fabricLoaderVersion}")
}

tasks.named<ProcessResources>("processResources") {
    val modProperties = modProperties.toMap()
    inputs.properties(modProperties)

    // Exclude .dev folders from mod resources
    exclude("**/.dev/**")

    filesMatching("fabric.mod.json") {
        expand(modProperties)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
}

java {
    // TODO: Common sources are not copied to loader-specific sources
    withSourcesJar()

    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.named<Jar>("jar") {
    val projectName = project.name
    inputs.property("projectName", projectName)
}

// configure the maven publication
publishing {
    publications {
        create<MavenPublication>("common") {
            from(components["java"])
        }
    }
}
