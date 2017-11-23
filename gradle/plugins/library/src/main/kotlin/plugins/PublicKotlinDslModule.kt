package plugins

import org.gradle.api.Plugin
import org.gradle.api.Project

import org.gradle.kotlin.dsl.*

import org.gradle.api.plugins.BasePluginConvention

import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

import tasks.GenerateClasspathManifest


/**
 * Configures a Gradle Kotlin DSL module for publication to artifactory.
 *
 * The published jar will:
 *  - be named after `base.archivesBaseName`
 *  - include all sources
 *  - contain a classpath manifest
 */
open class PublicKotlinDslModule : Plugin<Project> {

    override fun apply(project: Project) = project.run {

        plugins.apply(KotlinDslModule::class.java)
        plugins.apply("maven-publish")
        plugins.apply("com.jfrog.artifactory")

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
    }

    private
    val Project.base
        get() = convention.getPlugin(BasePluginConvention::class.java)

    private
    fun Project.publishing(action: PublishingExtension.() -> Unit) =
        extensions.configure(PublishingExtension::class.java, action)
}
