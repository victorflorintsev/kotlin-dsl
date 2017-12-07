import org.jetbrains.kotlin.gradle.plugin.KaptAnnotationProcessorOptions
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    kotlin("kapt") version embeddedKotlinVersion
}

val inferredPluginDeclarations = File(buildDir, "kebab/plugins.txt")
kapt {
    processors = "kebab.AutoPluginProcessor"
    arguments(
        delegateClosureOf<KaptAnnotationProcessorOptions> {
            arg("kebab.plugins.output.file", inferredPluginDeclarations)
        })
}

dependencies {
    kapt(project(":processor"))
    compile(project(":processor"))

    // fulfill step 1 of kotlin-library
    // compile(kotlin("stdlib"))
}

tasks {

    all { // kaptKotlin is not available immediately
        if (name == "kaptKotlin") {
            outputs.file(inferredPluginDeclarations)
        }
    }

    val inferGradlePluginDeclarations by creating {
        dependsOn("kaptKotlin")
        inputs.file(inferredPluginDeclarations)
        doLast {
            gradlePlugin {

                val pluginDeclarations = inferredPluginDeclarations
                    .readLines()
                    .filter { it.isNotBlank() }
                    .toSet()
                    .map { it.split('=', limit = 2) }

                for ((pluginId, pluginImplementation) in pluginDeclarations) {
                    plugins.create(pluginId) {
                        id = pluginId
                        implementationClass = pluginImplementation
                    }
                }
            }
        }
    }

    "pluginDescriptors" {
        dependsOn(inferGradlePluginDeclarations)
    }

    "compileKotlin"(KotlinCompile::class) {
        kotlinOptions {
            freeCompilerArgs += listOf(
                // enable nullability annotations
                "-Xjsr305=strict",
                // nevermind kotlin-compiler-embeddable copy of stdlib
                "-Xskip-runtime-version-check",
                // recognize *.gradle.kts files as Gradle Kotlin scripts
                "-script-templates", "${KotlinBuildScript::class.qualifiedName}")
        }
    }
}

dependencies {
    compile(kotlin("gradle-plugin", version = embeddedKotlinVersion))
    compile(kotlin("reflect", version = embeddedKotlinVersion))
}
