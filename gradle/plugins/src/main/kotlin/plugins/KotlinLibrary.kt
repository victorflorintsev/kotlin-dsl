package plugins

import org.gradle.api.Plugin
import org.gradle.api.Project

import org.gradle.kotlin.dsl.*

import org.jetbrains.kotlin.gradle.dsl.Coroutines
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

open class KotlinLibrary : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {

        plugins.apply("kotlin")

        kotlin {
            experimental.coroutines = Coroutines.ENABLE
        }

        tasks.withType<KotlinCompile> {
            kotlinOptions.apply {
                freeCompilerArgs = listOf(
                    "-Xjsr305=strict",
                    "-Xskip-runtime-version-check")
            }
        }
    }
}
