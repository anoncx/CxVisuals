import java.util.Properties

plugins {
    id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT"
}

val mc = providers.gradleProperty("mc").orElse(providers.gradleProperty("minecraft_version")).get()

val versionProps = Properties().apply {
    val file = layout.projectDirectory.file("versions/$mc.properties").asFile
    require(file.isFile) { "Unsupported Minecraft version $mc, missing ${file.name}" }
    file.inputStream().use { load(it) }
}

fun prop(name: String): String =
    providers.gradleProperty(name).orNull ?: versionProps.getProperty(name) ?: providers.gradleProperty(name).get()

group = prop("maven_group")
version = "${prop("mod_version")}+$mc"

base {
    archivesName = prop("archives_base_name")
}

val lib: SourceSet = sourceSets.create("lib") {
    java.srcDir("src/lib/java")
}

repositories {
    mavenCentral()
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") }
        filter { includeGroup("maven.modrinth") }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$mc")
    implementation("net.fabricmc:fabric-loader:${prop("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${prop("fabric_api_version")}")

    "libCompileOnly"("com.google.code.gson:gson:2.13.1")
    "libCompileOnly"("org.joml:joml:1.10.8")
    compileOnly("maven.modrinth:sodium:${prop("sodium_version")}")
    implementation(lib.output)
}

loom {
    mods {
        register("cxvisuals") {
            sourceSet(sourceSets.main.get())
            sourceSet(lib)
        }
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(prop("jdk_version").toInt()))
    }
}

tasks {
    processResources {
        val props = mapOf(
            "version" to project.version,
            "jdk_version" to prop("jdk_version"),
            "loader_version" to prop("loader_version"),
            "minecraft_range" to prop("minecraft_range")
        )
        inputs.properties(props)
        filesMatching("fabric.mod.json") {
            expand(props)
        }
    }

    jar {
        from(lib.output)
        filesMatching("cxvisuals.mixins.json") {
            filter { line: String -> line.replace("\"defaultRequire\": 1", "\"defaultRequire\": 0") }
        }
    }

    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }
}
