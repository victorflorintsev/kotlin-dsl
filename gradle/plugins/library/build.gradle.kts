import org.gradle.kotlin.dsl.support.ImplicitImports
import org.gradle.kotlin.dsl.support.serviceOf
import org.jetbrains.kotlin.gradle.plugin.KaptAnnotationProcessorOptions
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import kebab.*

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

dependencies {

    compile(kotlin("gradle-plugin", version = embeddedKotlinVersion))
    compile(kotlin("reflect", version = embeddedKotlinVersion))

    testCompile("junit:junit:4.11")
    testCompile("com.nhaarman:mockito-kotlin:1.5.0")
}

val scriptPluginFiles = fileTree("src/main/kotlin") {
    include("*.gradle.kts")
}

val scriptPlugins by lazy {
    scriptPluginFiles.map(::ScriptPlugin)
}

tasks {

    val inferGradlePluginDeclarations by creating {
        doLast {
            gradlePlugin {
                for (scriptPlugin in scriptPlugins) {
                    plugins.create(scriptPlugin.id) {
                        id = scriptPlugin.id
                        implementationClass = scriptPlugin.implementationClass
                    }
                }
            }
        }
    }

    "pluginDescriptors" {
        dependsOn(inferGradlePluginDeclarations)
    }

    val generatedSourcesDir = file("src/generated/kotlin")
    java.sourceSets["main"].withConvention(KotlinSourceSet::class) {
        kotlin.srcDir(generatedSourcesDir)
    }

    "clean"(Delete::class) {
        delete(generatedSourcesDir)
    }

    val generateScriptPluginWrappers by creating {
        inputs.files(scriptPluginFiles)
        outputs.dir(generatedSourcesDir)
        doLast {
            for (scriptPlugin in scriptPlugins) {
                scriptPlugin.writeScriptPluginWrapperTo(generatedSourcesDir)
            }
        }
    }

    val kotlinDslImplicitImportsFile = File(buildDir, "kotlin-dsl-implicit-imports")
    val kotlinDslImplicitImports by creating {
        outputs.file(kotlinDslImplicitImportsFile)
        doLast {
            val implicitImports = project.serviceOf<ImplicitImports>().list
            kotlinDslImplicitImportsFile
                .writeText(implicitImports.joinToString("\n"))
        }
    }

    "compileKotlin"(KotlinCompile::class) {
        dependsOn(kotlinDslImplicitImports)
        dependsOn(generateScriptPluginWrappers)
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
