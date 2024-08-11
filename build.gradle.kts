import net.fabricmc.loom.api.LoomGradleExtensionAPI
import java.util.*

plugins {
    id("architectury-plugin") version "3.4-SNAPSHOT"
    id("dev.architectury.loom") version "1.6-SNAPSHOT" apply false
    id("com.github.johnrengelman.shadow") version "8.1.1" apply false
}

val maxMcVersions: String by project
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
 * Create the `build.properties` file with Manifold preprocessor symbols
 * for known Minecraft versions.
 */
fun writeBuildProperties(maxVersions: List<String>, version: String) {
    /** Simple dotted versions split as lists of integers */
    fun String.toVersion() = split(".").map { it.toInt() }
    /** Semantic (lexicographic) ordering */
    operator fun List<Int>.compareTo(other: List<Int>): Int {
        for (i in indices) {
            if (i >= other.size) return 1
            (this[i] - other[i]).let { if (it != 0) return it }
        }
        return if (size == other.size) 0 else -1
    }
    
    val ver = version.toVersion()
    val maxLists = maxVersions.map { it.toVersion() }
    
    val redefineList = mutableListOf<String>()
    /** We add `PRE_MC_<v>` or `POST_MC_<v>` symbols for all versions,
     *  and `MC_<v>` for the current version. */
    fun addVersion(v: List<Int>) {
        val str = v.joinToString("_")
        if (ver < v) redefineList.add("PRE_MC_$str")
        if (ver == v) redefineList.add("MC_$str")
        if (ver >= v) redefineList.add("POST_MC_$str")
    }
    maxLists.forEach { maxVer ->
        if (maxVer.size >= 3) {
            // Add symbols for all known minor versions
            val major = maxVer.subList(0, maxVer.size - 1)
            val maxMinor = maxVer[maxVer.size - 1]
            addVersion(major)
            for (minor in 1 until maxMinor)
                addVersion(major + listOf(minor))
            addVersion(maxVer)
        } else addVersion(maxVer)
    }
    
    val sb = StringBuilder()
    
    // If this is a development build we add a DEV_BUILD symbol
    if (modVersion.lowercase().contains("dev")) {
        sb.append("DEV_BUILD")
        sb.append("=\n")
    }
    
    // Add the Minecraft version symbols
    for (redefinedVersion in redefineList) {
        sb.append(redefinedVersion)
        sb.append("=\n")
    }

    file("build.properties").writeText(sb.toString())
}


val versionProperties = mutableMapOf<String, String>()

/**
 * Load properties for the current Minecraft version from the relevant
 * file within the `versionProperties` folder.
 */
fun loadProperties() {
    val defaultMcVersion = "1.20.2"
    var mcVersion = ""
    val versions = mcVersions.split(Regex("""\s*+,\s*+"""))
    val maxMcVersions = maxMcVersions.split(Regex("""\s*+,\s*+"""))
    println("Available MC versions: $mcVersions")
    
    if (project.hasProperty("mcVersion")) {
        mcVersion = project.property("mcVersion") as String
        if (mcVersion !in versions)
            println("Warning: `mcVersion=$mcVersion` is not listed as one of the supported versions in `gradle.properties`")
    } else {
        println("No `mcVersion` specified! Defaulting to $defaultMcVersion.")
        println("Tip: Use `-PmcVersion='$defaultMcVersion'` in cmd arg to set `mcVersion`.")
    }
    
    println("Loading properties from `versionProperties/$mcVersion.properties`")
    Properties().apply {
        load(file("versionProperties/$mcVersion.properties").inputStream())
    }.forEach {
        rootProject.extra.set(it.key as String, it.value)
        versionProperties[it.key as String] = it.value as String
    }
    
    writeBuildProperties(maxMcVersions, mcVersion)
}

loadProperties()

val javaVersion: String by extra
val minecraftVersion: String by extra
val parchmentVersion: String by extra

// Alias within loom extension, as it also defines a `minecraftVersion` property
val buildMinecraftVersion get() = minecraftVersion

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
println("Mod properties:\n${modProperties.entries.joinToString("\n") {
    "  ${it.key} = ${it.value}"
}}")

architectury {
    minecraft = minecraftVersion
}

allprojects {
    version = modVersion
}

subprojects {
    apply(plugin = "dev.architectury.loom")
    apply(plugin = "architectury-plugin")
    apply(plugin = "maven-publish")

    group = mavenGroup

    extensions.configure<BasePluginExtension> {
        archivesName.set("$modId-${buildMinecraftVersion}-${project.name}")
    }

    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://maven.architectury.dev/")
        maven("https://maven.neoforged.net/releases/")

        maven("https://maven.parchmentmc.org") {
            name = "ParchmentMC"
        }

        // Manifold Preprocessor
        maven("https://oss.sonatype.org/content/repositories/snapshots/") {
            name = "Sonatype Snapshots"
        }

        maven("https://cursemaven.com") {
            name = "Curse Maven"
            content {
                includeGroup("curse.maven")
            }
        }
    }

    dependencies {
        "annotationProcessor"("systems.manifold:manifold-preprocessor:$manifoldVersion")
        "testAnnotationProcessor"("systems.manifold:manifold-preprocessor:$manifoldVersion")

        "testImplementation"("org.junit.jupiter:junit-jupiter-api:5.9.1")
        "testImplementation"("org.junit.jupiter:junit-jupiter-engine:5.9.1")
    }

    extensions.configure<LoomGradleExtensionAPI> {
        dependencies {
            "minecraft"("net.minecraft:minecraft:$buildMinecraftVersion")

            "mappings"(layered {
                officialMojangMappings()
                if (parchmentVersion.isNotBlank())
                    parchment("org.parchmentmc.data:parchment-$minecraftVersion:$parchmentVersion@zip")
            })
        }
    }

    tasks.withType<JavaCompile> {
        options.apply {
            encoding = "UTF-8"
            release.set(javaVersion.toInt())
            // if (project.name != "common")
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
