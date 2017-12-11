import org.gradle.kotlin.dsl.support.ImplicitImports
import org.gradle.kotlin.dsl.support.serviceOf
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

    testCompile("junit:junit:4.11")
    testCompile("com.nhaarman:mockito-kotlin:1.5.0")

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

    val kotlinDslImplicitImportsFile = File(buildDir, "kotlin-dsl-implicit-imports")
    val kotlinDslImplicitImports by creating {
        outputs.file(kotlinDslImplicitImportsFile)
        doLast {
            kotlinDslImplicitImportsFile.writeText(
                project.serviceOf<ImplicitImports>().list.joinToString("\n"))
        }
    }

    "compileKotlin"(KotlinCompile::class) {
        dependsOn(kotlinDslImplicitImports)
        inputs.file(kotlinDslImplicitImportsFile)
        kotlinOptions {
            freeCompilerArgs = listOf(
                // enable nullability annotations
                "-Xjsr305=strict",
                // nevermind kotlin-compiler-embeddable copy of stdlib
                "-Xskip-runtime-version-check",

                // The following would be part of the kotlin-dsl plugin (or some implied plugin)
                // recognize *.gradle.kts files as Gradle Kotlin scripts
                "-script-templates", "${KotlinBuildScript::class.qualifiedName}",
                // Propagate implicit imports and other settings
                "-Xscript-resolver-environment=kotlinDslImplicitImportsFile=\"$kotlinDslImplicitImportsFile\"")
        }
    }
}

dependencies {
    compile(kotlin("gradle-plugin", version = embeddedKotlinVersion))
    compile(kotlin("reflect", version = embeddedKotlinVersion))
}
