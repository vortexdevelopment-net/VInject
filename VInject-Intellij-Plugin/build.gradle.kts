plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.10"
    id("org.jetbrains.intellij.platform") version "2.6.0"
    id("com.gradleup.shadow") version "9.6.1"
}

group = "net.vortexdevelopment"
version = "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://jitpack.io")
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("org.jetbrains:annotations:26.0.1")
    implementation("net.vortexdevelopment:VInject-API:2.0.0-SNAPSHOT")
    implementation("net.vortexdevelopment:VInject-Analyzer:2.0.0-SNAPSHOT")
    implementation("io.github.cdagaming:DiscordIPC:1.0.0")
    implementation("com.google.code.gson:gson:2.10.1")
    intellijPlatform {
        intellijIdeaCommunity("2025.2.6")

        bundledPlugin("com.intellij.java")
    }
    // Note: Java 11+ HttpClient and javax.xml are built-in, no external dependencies needed
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

sourceSets {
    main {
        java.srcDirs("src/main/java")
        resources.srcDirs("src/main/resources")
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    withType<Jar> {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        manifest {
            attributes["Main-Class"] = "net.vortexdevelopment.plugin.vinject.Plugin"
        }
    }

    withType<ProcessResources> {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    shadowJar {
        archiveClassifier.set("shadow")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        // Include all dependencies in the shadow jar
        from(sourceSets.main.get().output)

        configurations = listOf(project.configurations.runtimeClasspath.get())

        // Include DiscordIPC and required dependencies
        dependencies {
            include(dependency("io.github.cdagaming:DiscordIPC:.*"))
            include(dependency("org.json:json:.*"))
            include(dependency("org.slf4j:slf4j-api:.*"))
            include(dependency("com.google.code.gson:gson:.*")) // include Gson in shadow jar
            // Exclude Kotlin/coroutines as they're provided by IntelliJ
            exclude(dependency("org.jetbrains.kotlin:.*"))
        }

        // Relocate to avoid conflicts
        relocate("org.json", "net.vortexdevelopment.plugin.vinject.lib.org.json")
        relocate("org.slf4j", "net.vortexdevelopment.plugin.vinject.lib.org.slf4j")
        relocate("com.google.gson", "net.vortexdevelopment.plugin.vinject.lib.com.google.gson") // relocate Gson

        exclude("META-INF/kotlin/**")
        exclude("META-INF/versions/**")

        // Exclude signing and manifest files
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
        exclude("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")

        mergeServiceFiles()
    }

    // Configure the regular jar task to include dependencies from shadowJar
    jar {
        // Include the shadowJar contents in the regular jar
        dependsOn(shadowJar)
        from(zipTree(shadowJar.get().archiveFile))

        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        // Exclude conflicting files again to be safe
        exclude("kotlin/**")
        exclude("kotlinx/**")
        exclude("META-INF/kotlin/**")
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }

    buildPlugin {
        // Now depends on the jar that includes our dependencies
        dependsOn(jar)
    }

    prepareSandbox {
        // Now depends on the jar that includes our dependencies
        dependsOn(jar)
    }

    patchPluginXml {
        sinceBuild.set("232.*")
        untilBuild.set("271.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
