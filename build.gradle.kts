@file:Suppress("UnstableApiUsage")

plugins {
    kotlin("jvm") version "1.9.20"
    id("com.vanniktech.maven.publish") version "0.25.3"
    application
}

group = "com.tomczyn.state"
version = "1.0"
val artifactId = "state-in-merge"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
}

application {
    mainClass.set("MainKt")
}

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.S01)
    signAllPublications()
}

mavenPublishing {
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
