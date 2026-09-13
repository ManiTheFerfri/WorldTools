plugins {
    kotlin("jvm") version "2.4.20"
    id("net.fabricmc.fabric-loom") version "1.17.20"
}

group = project.property("maven_group") as String
version = "${project.property("mod_version")}+${project.property("minecraft_version")}"

base {
    archivesName.set(project.property("archives_base_name") as String)
}

loom {
    accessWidenerPath.set(file("src/main/resources/worldtools.accesswidener"))
}

repositories {
    maven("https://maven.shedaniel.me/")
    maven("https://maven.terraformersmc.com/releases/")
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${project.property("fabric_loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_api_version")}")
    implementation("net.fabricmc:fabric-language-kotlin:${project.property("fabric_kotlin_version")}")
    implementation("me.shedaniel.cloth:cloth-config-fabric:${project.property("cloth_config_version")}") {
        exclude(group = "net.fabricmc.fabric-api", module = "fabric-api")
    }
    compileOnly("com.terraformersmc:modmenu:${project.property("mod_menu_version")}")
}

tasks.processResources {
    inputs.property("version", version)
    filesMatching("fabric.mod.json") {
        expand(
            mutableMapOf(
                "version" to version,
                "fabric_loader_version" to project.property("fabric_loader_version"),
                "fabric_kotlin_version" to project.property("fabric_kotlin_version")
            )
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

kotlin {
    jvmToolchain(25)
}
