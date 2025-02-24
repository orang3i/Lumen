import java.time.Duration

plugins {
    `java-library`
    alias(libs.plugins.blossom)

    `maven-publish`
    signing
    alias(libs.plugins.nexuspublish)
}

// Read env vars (used for publishing generally)
version = System.getenv("MINESTOM_VERSION") ?: "dev"
val channel = System.getenv("MINESTOM_CHANNEL") ?: "local" // local, snapshot, release

val shortDescription = "1.21 Lightweight Minecraft server"

allprojects {
    apply(plugin = "java")

    group = "net.minestom"
    version = rootProject.version
    description = shortDescription

    repositories {
        mavenLocal()
        mavenCentral()
    }

    configurations.all {
        // We only use Jetbrains Annotations
        exclude("org.checkerframework", "checker-qual")
    }

    java {
        withSourcesJar()
        withJavadocJar()

        toolchain.languageVersion = JavaLanguageVersion.of(21)
    }

    tasks.withType<Zip> {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    tasks.withType<Test> {
        useJUnitPlatform()

        // Viewable packets make tracking harder. Could be re-enabled later.
        jvmArgs("-Dminestom.viewable-packet=false")
        jvmArgs("-Dminestom.inside-test=true")
        minHeapSize = "512m"
        maxHeapSize = "1024m"
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}

sourceSets {
    main {
        java.srcDir(file("src/main/java"))
        java.srcDir(file("src/autogenerated/java"))
    }
}

dependencies {
    // Core dependencies
    api(libs.slf4j)
    api(libs.jetbrainsAnnotations)
    api(libs.bundles.adventure)
    implementation(libs.minestomData)
    implementation("de.articdive:jnoise-pipeline:4.1.0")


    // Performance/data structures
    api(libs.fastutil)
    implementation(libs.bundles.flare)
    api(libs.gson)
    implementation(libs.jcTools)



    // Testing
    (libs.bundles.junit)
    testImplementation(project(":testing"))
}

tasks {
    jar {
        manifest {
            attributes("Automatic-Module-Name" to "net.minestom.server")
        }
    }
    withType<Javadoc> {
        (options as? StandardJavadocDocletOptions)?.apply {
            encoding = "UTF-8"

            // Custom options
            addBooleanOption("html5", true)
            addStringOption("-release", "21")
            // Links to external javadocs
            links("https://docs.oracle.com/en/java/javase/21/docs/api/")
            links("https://jd.advntr.dev/api/${libs.versions.adventure.get()}/")
        }
    }

    blossom {
        val gitFile = "src/main/java/net/minestom/server/Git.java"

        val gitCommit = System.getenv("GIT_COMMIT")
        val gitBranch = System.getenv("GIT_BRANCH")
        val group = System.getenv("GROUP")
        val artifact = System.getenv("ARTIFACT")

        replaceToken("\"&COMMIT\"", if (gitCommit == null) "null" else "\"${gitCommit}\"", gitFile)
        replaceToken("\"&BRANCH\"", if (gitBranch == null) "null" else "\"${gitBranch}\"", gitFile)
        replaceToken("\"&GROUP\"", if (group == null) "null" else "\"${group}\"", gitFile)
        replaceToken("\"&ARTIFACT\"", if (artifact == null) "null" else "\"${artifact}\"", gitFile)
    }

    nexusPublishing {
        useStaging.set(true)
        this.packageGroup.set("net.minestom")

        transitionCheckOptions {
            maxRetries.set(360) // 1 hour
            delayBetween.set(Duration.ofSeconds(10))
        }

        repositories.sonatype {
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))

            if (System.getenv("SONATYPE_USERNAME") != null) {
                username.set(System.getenv("SONATYPE_USERNAME"))
                password.set(System.getenv("SONATYPE_PASSWORD"))
            }
        }
    }

    publishing.publications.create<MavenPublication>("maven") {
        groupId = "net.minestom"
        // todo: decide on publishing scheme
        artifactId = if (channel == "snapshot") "minestom-snapshots" else "minestom-snapshots"
        version = project.version.toString()

        from(project.components["java"])

        pom {
            name.set(this@create.artifactId)
            description.set(shortDescription)
            url.set("https://github.com/minestom/minestom")

            licenses {
                license {
                    name.set("Apache 2.0")
                    url.set("https://github.com/minestom/minestom/blob/main/LICENSE")
                }
            }

            developers {
                developer {
                    id.set("TheMode")
                }
                developer {
                    id.set("mworzala")
                    name.set("Matt Worzala")
                    email.set("matt@hollowcube.dev")
                }
            }

            issueManagement {
                system.set("GitHub")
                url.set("https://github.com/minestom/minestom/issues")
            }

            scm {
                connection.set("scm:git:git://github.com/minestom/minestom.git")
                developerConnection.set("scm:git:git@github.com:minestom/minestom.git")
                url.set("https://github.com/minestom/minestom")
                tag.set("HEAD")
            }

            ciManagement {
                system.set("Github Actions")
                url.set("https://github.com/minestom/minestom/actions")
            }
        }
    }

    signing {
        isRequired = System.getenv("CI") != null

        val privateKey = System.getenv("GPG_PRIVATE_KEY")
        val keyPassphrase = System.getenv()["GPG_PASSPHRASE"]
        useInMemoryPgpKeys(privateKey, keyPassphrase)

        sign(publishing.publications)
    }
}
