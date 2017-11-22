rootProject.name = "gradle-kotlin-dsl"

include(
    "provider",
    "tooling-models",
    "tooling-builders",
    "plugins",
    "test-fixtures",
    "samples-tests")

// Begin `local-plugins` plugin implementation

// 1. include gradle/plugins if present
includeBuild("gradle/plugins") {
    dependencySubstitution {
        substitute(module("local-plugins:library")).with(project(":"))
    }
}

// 2. inject gradle/plugins in the root project buildscript classpath
gradle.addProjectEvaluationListener(object : ProjectEvaluationListener {

    override fun beforeEvaluate(project: Project) {
        if (project.rootProject == project) {
            project.buildscript.dependencies.add("classpath", "local-plugins:library:1.0")
        }
    }

    override fun afterEvaluate(project: Project, state: ProjectState) = Unit
})
