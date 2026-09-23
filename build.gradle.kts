import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.api.tasks.Exec

plugins {
    `java-library`
    jacoco
}

group = "nl.hauntedmc.paperhotreloader"
version = "1.0.4"

val javaVersion = 25
val paperApiVersion = "26.2.build.124-stable"
val acceptanceCompileClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // This plugin uses only the public Bukkit/Paper API. Unlike AIlex, it has no NMS classes to remap.
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    testImplementation("io.papermc.paper:paper-api:$paperApiVersion")
    acceptanceCompileClasspath("io.papermc.paper:paper-api:$paperApiVersion")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    compileJava {
        options.release.set(javaVersion)
        options.encoding = Charsets.UTF_8.name()
    }

    processResources {
        val properties = mapOf("version" to project.version)
        inputs.properties(properties)
        filteringCharset = Charsets.UTF_8.name()
        filesMatching("plugin.yml") {
            expand(properties)
        }
    }

    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
        finalizedBy(jacocoTestReport)
    }

    check {
        dependsOn(jacocoTestReport)
    }
}

tasks.withType<JacocoReport>().configureEach {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

val acceptanceTest = tasks.register<Exec>("acceptanceTest") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Boots Paper and verifies PHR against temporary sample plugins."
    dependsOn(tasks.jar)
    commandLine("bash", "src/acceptance/run-acceptance.sh")
    doFirst {
        environment("PHR_ARTIFACT", tasks.jar.get().archiveFile.get().asFile.absolutePath)
        environment("PHR_PAPER_API_CLASSPATH", acceptanceCompileClasspath.asPath)
    }
}
