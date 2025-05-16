plugins {
	id("java")
	id("dev.architectury.loom") version("1.7-SNAPSHOT")
	id("architectury-plugin") version("3.4-SNAPSHOT")
	kotlin("jvm") version "1.9.23"
}

version = "1.0.0"
group = "com.clokkworkk"

architectury {
	platformSetupLoomIde()
	fabric()
}

loom {
	silentMojangMappingsLicense()
	mixin {
		defaultRefmapName.set("mixins.${project.name}.refmap.json")
	}
}

base {
	archivesName = "pokepatcher"
}

repositories {
	// Add repositories to retrieve artifacts from in here.
	// You should only use this when depending on other mods because
	// Loom adds the essential maven repositories to download Minecraft and libraries from automatically.
	// See https://docs.gradle.org/current/userguide/declaring_repositories.html
	// for more information about repositories.
	mavenCentral()
	maven {
		name = "Geckolib"
		url = uri("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/")
	}
	maven {
		name = "ImpactDev"
		url = uri("https://maven.impactdev.net/repository/development/")
	}
	maven {
		name = "Sonatype Snapshots"
		url = uri("https://oss.sonatype.org/content/repositories/snapshots")
	}

	maven {
		name = "Modrinth"
		url = uri("https://api.modrinth.com/maven")
	}
	maven {
		name = "TerraformersMC"
		url = uri("https://maven.terraformersmc.com/")
	}
	maven {
		name = "Ladysnake Libs"
		url = uri("https://maven.ladysnake.org/releases")
	}

}

fabricApi {
	configureDataGeneration()
}

dependencies {
	// To change the versions see the gradle.properties file
	minecraft("net.minecraft:minecraft:1.21.1")
	mappings("net.fabricmc:yarn:1.21.1+build.3:v2")
	modImplementation("net.fabricmc:fabric-loader:0.16.14")

	modImplementation("net.fabricmc:fabric-language-kotlin:1.12.3+kotlin.2.0.21")
	testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
	testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")


	// Fabric API. This is technically optional, but you probably want it anyway.
	modImplementation("net.fabricmc.fabric-api:fabric-api:0.116.0+1.21.1")

	modImplementation("com.cobblemon:fabric:1.6.1+1.21.1")
	
}


tasks.processResources {
	inputs.property("version", project.version)

	filesMatching("fabric.mod.json") {
		expand(project.properties)
	}
}

//tasks.withType(JavaCompile).configureEach {
//	it.options.release = 21
//}

java {
	// Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
	// if it is present.
	// If you remove this line, sources will not be generated.
	withSourcesJar()

	sourceCompatibility = JavaVersion.VERSION_21
	targetCompatibility = JavaVersion.VERSION_21
}

//jar {
//	inputs.property("archivesName", project.base.archivesName)
//
//	from("LICENSE") {
//		rename { "${it}_${inputs.properties.archivesName}"}
//	}
//}

// configure the maven publication
//publishing {
//	publications {
//		create("mavenJava", MavenPublication) {
//			artifactId = project.archives_base_name
//			from components.java
//		}
//	}
//
//	// See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
//	repositories {
//		// Add repositories to publish to here.
//		// Notice: This block does NOT have the same function as the block in the top level.
//		// The repositories here will be used for publishing your artifact, not for
//		// retrieving dependencies.
//	}
//}