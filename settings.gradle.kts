rootProject.name = "gradle-kotlin-dsl"

include(
    "provider",
    "tooling-models",
    "tooling-builders",
    "plugins",
    "test-fixtures",
    "samples-tests")

includeBuild("gradle/plugins") {
    dependencySubstitution {
        substitute(module("local-plugins:library")).with(project(":"))
    }
}

pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id.startsWith("local-")) {
                useModule("local-plugins:library:1.0")
            }
        }
    }
}
