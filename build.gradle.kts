import java.io.BufferedReader
import java.io.FileReader

plugins {
    id("com.android.library").version("8.0.1").apply(false)
    kotlin("multiplatform").version("1.8.20").apply(false)
}

val props = java.util.Properties()
FileReader("maven.properties").use { fileReader ->
    BufferedReader(fileReader).use { bufferedReader ->
        bufferedReader.readLines().forEach { line ->
            if (line.isNotBlank()) {
                val values = line.split("=", limit = 2)
                props[values[0]] = values[1]
            }
        }
    }
}

allprojects {
    repositories {
        mavenCentral()
    }

    group = "com.tomczyn"
    version = "1.0"

    apply(plugin = "maven-publish")
    apply(plugin = "signing")


    extensions.configure<PublishingExtension> {
        repositories {
            maven {
                val isSnapshot = version.toString().endsWith("SNAPSHOT")
                url = uri(
                    if (!isSnapshot) "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2"
                    else "https://s01.oss.sonatype.org/content/repositories/snapshots"
                )

                credentials {
                    username = props["ossrhUsername"].toString()
                    password = props["ossrhPassword"].toString()
                }
            }
        }

        val javadocJar = tasks.register<Jar>("javadocJar") {
            archiveClassifier.set("javadoc")
        }

        publications {
            withType<MavenPublication> {
                artifact(javadocJar)

                pom {
                    name.set("state-in-merge")
                    description.set("MutableStateFlow extension for better state management ")
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://github.com/tomczyn/state-in-merge/blob/main/LICENSE")
                        }
                    }
                    url.set("https://github.com/tomczyn/state-in-merge")
                    issueManagement {
                        system.set("Github")
                        url.set("https://github.com/tomczyn/state-in-merge/issues")
                    }
                    scm {
                        connection.set("https://github.com/tomczyn/state-in-merge.git")
                        url.set("https://github.com/tomczyn/state-in-merge")
                    }
                    developers {
                        developer {
                            name.set("Maciej Tomczynski")
                            email.set("dev@tomczyn.com")
                        }
                    }
                }
            }
        }
    }

    val publishing = extensions.getByType<PublishingExtension>()
    extensions.configure<SigningExtension> {
        useInMemoryPgpKeys(
            props["signing.keyId"].toString(),
            props["signing.key"].toString(),
            props["signing.password"].toString(),
        )

        sign(publishing.publications)
    }
}