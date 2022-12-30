val prop = rootProject.extra

val modId: String by rootProject
val enabledPlatforms: String by rootProject
val minecraftVersion: String by prop
val fabricLoaderVersion: String by prop

architectury {
    common(enabledPlatforms.split(Regex("""\s*+,\s*+""")))
}

loom {
    accessWidenerPath.set(file("src/main/resources/$modId.accesswidener"))
}

dependencies {
    modImplementation("net.fabricmc:fabric-loader:${fabricLoaderVersion}")
    
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.0")
}

tasks.processResources {
    // Exclude .dev folders from the mod resources
    exclude("**/.dev/**")
}

publishing {
    publications {
        // register<MavenPublication>("common") {
        //     artifactId = "$modId-$minecraftVersion-${project.name}"
        //     version = rootProject.version.toString()
        //
        //     from(components["java"])
        // }
    }
}