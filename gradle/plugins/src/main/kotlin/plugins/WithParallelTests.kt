package plugins

import org.gradle.api.Plugin
import org.gradle.api.Project

open class WithParallelTests : Plugin<Project> {
    override fun apply(target: Project) {
        Class
            .forName("With_parallel_tests_gradle")
            .getDeclaredConstructor(Project::class.java)
            .newInstance(target)
    }
}
