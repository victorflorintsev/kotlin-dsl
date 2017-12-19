val customInstallation by rootProject.tasks
tasks {
    "test" {
        dependsOn(customInstallation)
    }
}