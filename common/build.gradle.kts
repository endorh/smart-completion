val modId: String by rootProject
val enabledPlatforms: String by rootProject
val minecraftVersion: String by rootProject
val fabricLoaderVersion: String by rootProject

architectury {
    common(enabledPlatforms.split(","))
}

loom {
    accessWidenerPath.set(file("src/main/resources/$modId.accesswidener"))
}

dependencies {
    modImplementation("net.fabricmc:fabric-loader:${fabricLoaderVersion}")
    
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.0")
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