import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    kotlin("kapt") version embeddedKotlinVersion
}

kapt {
    processors = "kebab.AutoPluginProcessor"
}

dependencies {
    kapt(project(":processor"))
    compile(project(":processor"))
}

tasks {
    val inferGradlePluginDeclarations by creating {
        doLast {
            gradlePlugin {
                (plugins) {
                    "kotlinLibrary" {
                        id = "kotlin-library"
                        implementationClass = "plugins.KotlinLibrary"
                    }
                    "kotlinDslModule" {
                        id = "kotlin-dsl-module"
                        implementationClass = "plugins.KotlinDslModule"
                    }
                    "publicKotlinDslModule" {
                        id = "public-kotlin-dsl-module"
                        implementationClass = "plugins.PublicKotlinDslModule"
                    }
                    "withParallelTests" {
                        id = "with-parallel-tests"
                        implementationClass = "plugins.WithParallelTests"
                    }
                }
            }
        }
    }

    "pluginDescriptors" {
        dependsOn(inferGradlePluginDeclarations)
    }

    withType<KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf(
                // enable nullability annotations
                "-Xjsr305=strict",
                // nevermind kotlin-compiler-embeddable copy of stdlib
                "-Xskip-runtime-version-check",
                // recognize *.gradle.kts files as Gradle Kotlin scripts
                "-script-templates", "${KotlinBuildScript::class.qualifiedName}"
            )
        }
    }
}

dependencies {
    compile(kotlin("gradle-plugin", version = embeddedKotlinVersion))
    compile(kotlin("reflect", version = embeddedKotlinVersion))
}
