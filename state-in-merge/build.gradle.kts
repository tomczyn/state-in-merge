@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.BufferedReader
import java.io.FileReader
import java.util.*

plugins {
    kotlin("jvm") version "1.9.20"
    id("com.vanniktech.maven.publish") version "0.25.3"
}

group = "com.tomczyn.coroutines"
version = "0.0.3"
val artifactId = "state-in-merge"

repositories {
    mavenCentral()
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(11)
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        freeCompilerArgs += "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
    }
}

val props = Properties()
FileReader("${rootDir}/${artifactId}/maven.properties").use { fileReader ->
    BufferedReader(fileReader).use { bufferedReader ->
        bufferedReader.readLines().forEach { line ->
            if (line.isNotBlank()) {
                val values = line.split("=", limit = 2)
                ext[values[0]] = values[1]
                props[values[0]] = values[1]
            }
        }
    }
}

publishing {
    repositories {
        maven {
            setUrl("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = props["mavenCentralUsername"].toString()
                password = props["mavenCentralPassword"].toString()
            }
        }
    }
}

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.S01)
    signAllPublications()
    coordinates(group.toString(), artifactId, version.toString())
    pom {
        name.set("state-in-merge")
        description.set("Extension for MutableStateFlow for better state management ")
        inceptionYear.set("2023")
        url.set("https://github.com/tomczyn/state-in-merge")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://github.com/tomczyn/state-in-merge/blob/main/LICENSE")
            }
        }
        developers {
            developer {
                id.set("tomczyn")
                name.set("Maciej Tomczynski")
                url.set("https://github.com/tomczyn/")
            }
        }
        scm {
            url.set("https://github.com/tomczyn/state-in-merge/")
            connection.set("scm:git:git://github.com/tomczyn/state-in-merge.git")
            developerConnection.set("scm:git:ssh://git@github.com/tomczyn/state-in-merge.git")
        }
    }
}
