plugins {
    kotlin("jvm") version "1.8.0"
    `java-gradle-plugin`
    `maven-publish`
}

val archivesBaseName = "asbestos"
group = "io.github.prismwork"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net") { name = "Fabric" } // mapping-io
    maven("https://maven.quiltmc.org/repository/snapshot") { name = "Quilt" } // quilt loom
}

dependencies {
    implementation(gradleApi())

    compileOnly("org.quiltmc:loom:${property("loom_version")}-SNAPSHOT")

    implementation("com.google.guava:guava:31.1-jre")
    implementation("net.fabricmc:mapping-io:0.2.1")
}

kotlin {
    jvmToolchain(17)
}

java {
    withSourcesJar()
}

gradlePlugin {
    plugins {
        create("asbestos") {
            id = "io.github.prismwork.asbestos"
            implementationClass = "io.github.prismwork.asbestos.AsbestosGradlePlugin"
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group as String
            artifactId = project.name
            version = project.version as String

            from(components["java"])
        }
    }

    repositories {
        mavenLocal()
    }
}
