includeBuild("build-logic") {
    dependencySubstitution {
        substitute(module("composite-build-logic:build-logic")).with(project(":"))
    }
}

// Work around for https://github.com/gradle/gradle-native/issues/522
pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if(requested.id.id == "included-plugin") {
                useModule("composite-build-logic:build-logic:latest-integration")
            }
        }
    }
}
