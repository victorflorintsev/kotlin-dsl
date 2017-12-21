package kebab

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.ProjectEvaluationListener
import org.gradle.api.ProjectState
import org.gradle.api.initialization.Settings


open class LocalPluginsPlugin : Plugin<Settings> {

    override fun apply(settings: Settings): Unit = settings.run {

        // 1. include gradle/plugins if present
        includeBuild("gradle/plugins") {
            it.dependencySubstitution {
                it.substitute(
                    it.module("local-plugins:library")).with(
                        it.project(":library"))
            }
        }

        // 2. inject gradle/plugins in the root project buildscript classpath
        gradle.addProjectEvaluationListener(
            object : ProjectEvaluationListener {

                override fun beforeEvaluate(project: Project) {
                    if (project.rootProject == project) {
                        project.buildscript.dependencies.add(
                            "classpath",
                            "local-plugins:library:1.0")
                    }
                }

                override fun afterEvaluate(project: Project, state: ProjectState) = Unit
            })
    }
}
