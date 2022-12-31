import net.fabricmc.loom.api.LoomGradleExtensionAPI
import java.util.*

plugins {
    id("architectury-plugin") version "3.4-SNAPSHOT"
    id("dev.architectury.loom") version "1.0.+" apply false
}

val mcVersions: String by project
val mcVersion: String by project

val manifoldVersion: String by project
val mavenGroup: String by project

val modId: String by project
val modName: String by project
val modVersion: String by project
val modDescription: String by project
val modAuthor: String by project
val modLicense: String by project
val modPage: String by project
val modSource: String by project
val modIssueTracker: String by project
val modUpdatesJSON: String by project
val githubRepo: String by project
val modDisplayTest: String by project

/**
 * Create the `build.properties` file with Manifold preprocessor symbols.
 */
fun writeBuildProperties(versions: List<String>, idx: Int) {
    val redefineList = mutableListOf<String>()
    for (i in versions.indices) {
        val mcStr = versions[i].replace(".", "_")
        if (idx < i) redefineList.add("PRE_MC_$mcStr")
        if (idx == i) redefineList.add("MC_$mcStr")
        if (idx >= i) redefineList.add("POST_MC_$mcStr")
    }
    
    val sb = StringBuilder()
    
    // Check if this is a development build
    if (modVersion.toLowerCase().contains("dev")) {
        // Use this only for logging to avoid parity issues with releases
        sb.append("DEV_BUILD")
        sb.append("=\n")
    }
    
    // Minecraft version symbols
    for (redefinedVersion in redefineList) {
        sb.append(redefinedVersion)
        sb.append("=\n")
    }
    
    file("build.properties").writeText(sb.toString())
}

val versionProperties = mutableMapOf<String, String>()

/**
 * Load properties for the current Minecraft version
 */
fun loadProperties() {
    val defaultMcVersion = "1.19.2"
    var mcVersion = ""
    val versions = mcVersions.split(Regex("""\s*+,\s*+"""))
    var mcIndex = -1
    println("Available MC versions: $mcVersions")
    
    if (project.hasProperty("mcVersion")) {
        mcVersion = project.property("mcVersion") as String
        mcIndex = versions.indexOf(mcVersion)
    }
    
    if (mcIndex == -1) {
        if (mcVersion.isNotEmpty()) {
            println("Invalid `mcVersion`: $mcVersion! Defaulting to $defaultMcVersion.")
        } else println("No `mcVersion` specified! Defaulting to $defaultMcVersion.")
        println("Tip: Use `-PmcVersion='$defaultMcVersion'` in cmd arg to set `mcVersion`.")
        
        mcVersion = defaultMcVersion
        mcIndex = versions.indexOf(defaultMcVersion)
        
        assert(mcIndex != -1)
    }
    
    println("Loading properties from $mcVersion.properties:")
    val props = Properties()
    props.load(file("versionProperties/$mcVersion.properties").inputStream())
    
    props.forEach {
        rootProject.extra.set(it.key as String, it.value)
        versionProperties[it.key as String] = it.value as String
    }
    
    writeBuildProperties(versions, mcIndex)
}

loadProperties()

val javaVersion: String by extra
val minecraftVersion: String by extra
val parchmentVersion: String by extra

val modProperties by extra {
    val map = mutableMapOf(
        "modId" to modId,
        "modName" to modName,
        "version" to modVersion,
        "modDescription" to modDescription.replace("\n", "\\n"),
        "modAuthor" to modAuthor,
        "modLicense" to modLicense,
        "modPage" to modPage,
        "modSource" to modSource,
        "modIssueTracker" to modIssueTracker,
        "modUpdatesJSON" to modUpdatesJSON,
        "githubRepo" to githubRepo,
        "modDisplayTest" to modDisplayTest,
    )
    map += versionProperties
    map.toMap()
}

// Log build properties
fun sysProp(name: String) = System.getProperty(name)
println(
    "Mod: \"$displayName\" ($modId), version: ${minecraftVersion}-${modVersion}")
println(
    "Java: ${sysProp("java.version")}, " +
    "JVM: ${sysProp("java.vm.version")}(${sysProp("java.vendor")}), " +
    "Arch: ${sysProp("os.arch")}")
println("Mod properties:")
modProperties.forEach { (k, v) ->
    println("  $k = $v")
}

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
    
    dependencies {
        "annotationProcessor"("systems.manifold:manifold-preprocessor:$manifoldVersion")
        "testAnnotationProcessor"("systems.manifold:manifold-preprocessor:$manifoldVersion")
        
        "testImplementation"("org.junit.jupiter:junit-jupiter-api:5.9.1")
        "testImplementation"("org.junit.jupiter:junit-jupiter-engine:5.9.1")
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
        // Manifold Preprocessor
        maven("https://oss.sonatype.org/content/repositories/snapshots/") {
            name = "Sonatype Snapshots"
        }
    }
    
    tasks.withType<JavaCompile> {
        options.apply {
            encoding = "UTF-8"
            release.set(javaVersion.toInt())
            compilerArgs.add("-Xplugin:Manifold")
        }
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