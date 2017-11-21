package plugins

import org.gradle.kotlin.dsl.*

import org.gradle.api.Plugin
import org.gradle.api.Project

import org.gradle.api.plugins.JavaPluginConvention

import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test

import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.gradle.api.internal.initialization.DefaultClassLoaderScope


/**
 * Configures a Gradle Kotlin DSL module.
 *
 * The assembled jar will:
 *  - be named after `base.archivesBaseName`
 *  - include all sources
 */
open class KotlinDslModule : Plugin<Project> {

    override fun apply(project: Project) {

        project.run {

            plugins.apply(KotlinLibrary::class.java)

            // including all sources
            val mainSourceSet = java.sourceSets.getByName("main")
            afterEvaluate {
                tasks {
                    "jar"(Jar::class) {
                        from(mainSourceSet.allSource)
                        manifest.attributes.apply {
                            put("Implementation-Title", "Gradle Kotlin DSL (${project.name})")
                            put("Implementation-Version", version)
                        }
                    }
                }
            }

            // sets the Gradle Test Kit user home into custom installation build dir
            if (hasProperty(kotlinDslDebugPropertyName) && findProperty(kotlinDslDebugPropertyName) != "false") {
                tasks.withType(Test::class.java) {
                    systemProperty(
                        "org.gradle.testkit.dir",
                        "${rootProject.buildDir}/custom/test-kit-user-home")
                }
            }

            withTestStrictClassLoading()
            withTestWorkersMemoryLimits()
        }
    }
}


internal
fun Project.kotlin(action: KotlinProjectExtension.() -> Unit) =
    extensions.configure(KotlinProjectExtension::class.java, action)


internal
val Project.java
    get() = convention.getPlugin(JavaPluginConvention::class.java)


fun Project.withTestStrictClassLoading() {
    tasks.withType(Test::class.java) {
        systemProperty(DefaultClassLoaderScope.STRICT_MODE_PROPERTY, true)
    }
}


fun Project.withTestWorkersMemoryLimits(min: String = "64m", max: String = "128m") {
    tasks.withType(Test::class.java) {
        jvmArgs("-Xms$min", "-Xmx$max")
    }
}


val kotlinDslDebugPropertyName = "org.gradle.kotlin.dsl.debug"

