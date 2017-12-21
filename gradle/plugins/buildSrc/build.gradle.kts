plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

gradlePlugin {
    (plugins) {
        "kotlin-dsl-plugins" {
            id = "kotlin-dsl-plugins"
            implementationClass = "kebab.KotlinDslPlugins"
        }
    }
}

dependencies {
    compile(kotlin("gradle-plugin"))
}

repositories {
    gradlePluginPortal()
}
