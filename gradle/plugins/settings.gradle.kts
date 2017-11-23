rootProject.apply {
    name = "local-plugins" // avoids conflict with local project named `plugins`
    buildFileName = "plugins.gradle.kts"
}

include("library")

include("processor")
