/**
 * Configures a Gradle Kotlin DSL module for publication to artifactory.
 *
 * The published jar will:
 *  - be named after `base.archivesBaseName`
 *  - include all sources
 *  - contain a classpath manifest
 */

import org.gradle.api.publish.maven.MavenPublication
import tasks.GenerateClasspathManifest

apply {
    plugin("kotlin-dsl-module")
    plugin("maven-publish")
    plugin("com.jfrog.artifactory")
}

publishing {
    (publications) {
        "mavenJava"(MavenPublication::class) {
            from(components["java"])
            artifactId = base.archivesBaseName
        }
    }
}

tasks {
    "artifactoryPublish" {
        dependsOn("jar")
    }
}

// classpath manifest
val generatedResourcesDir = file("$buildDir/generate-resources/main")
val generateClasspathManifest by tasks.creating(GenerateClasspathManifest::class) {
    outputDirectory = generatedResourcesDir
}
val mainSourceSet = java.sourceSets["main"]
mainSourceSet.output.dir(
    mapOf("builtBy" to generateClasspathManifest),
    generatedResourcesDir)

