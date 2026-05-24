plugins {
    id("net.fabricmc.fabric-loom")
    // id("maven-publish")
}

val prop = rootProject.extra

val modId: String by rootProject
val modVersion: String by rootProject
val minecraftVersion: String by prop
val fabricLoaderVersion: String by prop
val fabricApiVersion: String by prop

val modProperties: Map<String, String> by prop

loom {
    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
        }
    }
}

val commonImplementation by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
    isTransitive = false
}
configurations {
    api.get().extendsFrom(commonImplementation)
}

dependencies {
    commonImplementation(project(":common"))

    // To change the versions see the gradle.properties file
    minecraft("com.mojang:minecraft:${minecraftVersion}")

    // Fabric loader
    implementation("net.fabricmc:fabric-loader:${fabricLoaderVersion}")

    // Fabric API
    implementation("net.fabricmc.fabric-api:fabric-api:${fabricApiVersion}")
}

val copyCommonClasses by tasks.registering(Copy::class) {
    mustRunAfter(":common:compileJava")
    mustRunAfter(":common:compileTestJava")
    mustRunAfter(tasks.compileJava.get())

    from(project(":common").layout.buildDirectory.get().dir("classes"))
    into(project.layout.buildDirectory.dir("classes"))
}

val copyCommonResources by tasks.registering(Copy::class) {
    mustRunAfter(":common:processResources")
    mustRunAfter(":common:processTestResources")
    mustRunAfter(":common:compileTestJava")
    mustRunAfter(tasks.processResources.get())
    mustRunAfter(tasks.processTestResources.get())

    from(project(":common").layout.buildDirectory.dir("resources"))
    into(project.layout.buildDirectory.dir("resources"))
}

tasks.classes.configure {
    dependsOn(copyCommonClasses)
    dependsOn(copyCommonResources)
}

tasks.named<ProcessResources>("processResources") {
    val modProperties = modProperties.toMap()
    inputs.properties(modProperties)

    // Exclude .dev folders from the mod resources
    exclude("**/.dev/**")

    filesMatching("fabric.mod.json") {
        expand(modProperties)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
}

java {
    withSourcesJar()

    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}


tasks.named<Jar>("jar") {
    val modId = modId
    inputs.property("projectName", modId)

    from(rootProject.projectDir.resolve("LICENSE")) {
        rename {
            "${it}_${modId}"
        }
    }
}

// configure the maven publication
publishing {
    publications {
        create<MavenPublication>("fabric") {
            artifactId = "$modId-$minecraftVersion-${project.name}"
            version = modVersion

            from(components["java"])
        }
    }

    // See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
    repositories {
        // Add repositories to publish to here.
        // Notice: This block does NOT have the same function as the block in the top level.
        // The repositories here will be used for publishing your artifact, not for
        // retrieving dependencies.
    }
}
