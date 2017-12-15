import org.gradle.kotlin.dsl.support.ImplicitImports
import org.gradle.kotlin.dsl.support.serviceOf

import org.jetbrains.kotlin.gradle.plugin.KaptAnnotationProcessorOptions
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

import kebab.*

import org.gradle.api.internal.artifacts.dependencies.DefaultSelfResolvingDependency
import org.gradle.api.internal.file.DefaultFileCollectionFactory
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.initialization.ScriptHandlerInternal
import org.gradle.api.internal.plugins.PluginManagerInternal
import org.gradle.api.internal.project.ProjectInternal

import org.gradle.groovy.scripts.StringScriptSource

import org.gradle.internal.classpath.DefaultClassPath

import org.gradle.kotlin.dsl.accessors.accessorsClassPathFor
import org.gradle.kotlin.dsl.provider.KotlinScriptClassPathProvider

import org.gradle.plugin.management.internal.DefaultPluginRequests
import org.gradle.plugin.use.internal.PluginRequestApplicator
import org.gradle.plugin.use.internal.PluginRequestCollector
import org.gradle.plugin.use.resolve.internal.ArtifactRepositoriesPluginResolver

import org.gradle.testfixtures.ProjectBuilder


plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
     // both to be replaced by `kotlin-dsl-plugins`
}

dependencies {

    // Express global plugin dependencies using a superset of the plugins DSL
    plugins {
        kotlin("jvm") version embeddedKotlinVersion
        id("com.jfrog.artifactory") version "4.1.1" accessors false // opt-out of static accessors for elements contributed by this plugin
    }

    // and/or introduce the `plugin` dependency notation
    compile(plugin("org.jetbrains.kotlin.jvm", version = embeddedKotlinVersion))
    compile(plugin("com.jfrog.artifactory", version = "4.1.1"))

    testCompile("junit:junit:4.11")
    testCompile("com.nhaarman:mockito-kotlin:1.5.0")
}

repositories {
    gradlePluginPortal()
}


// ----------------------------------------------------------------------------
// `kotlin-dsl-plugins` plugin implementation follows
// ----------------------------------------------------------------------------


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

    val baseAccessorsBuildDir = buildDir / "kotlin-dsl-accessors"
    val generatedAccessorsFile = baseAccessorsBuildDir / "accessors.kt"
    val generateAccessors by creating {
        inputs.files(project.configurations.compile)
        outputs.file(generatedAccessorsFile)
        doLast {
            val classPath = project.configurations.compile.files

            val syntheticRootProject = ProjectBuilder()
                .withProjectDir(baseAccessorsBuildDir / "synthetic-project")
                .build()

            val scriptHandler = syntheticRootProject.buildscript as ScriptHandlerInternal
            scriptHandler.addScriptClassPathDependency(
                DefaultSelfResolvingDependency(
                    fileCollectionOf(classPath, "kotlin-dsl-accessors-classpath") as FileCollectionInternal))
            syntheticRootProject.serviceOf<PluginRequestApplicator>().apply {
                applyPlugins(
                    DefaultPluginRequests.EMPTY,
                    scriptHandler,
                    syntheticRootProject.pluginManager as PluginManagerInternal,
                    (syntheticRootProject as ProjectInternal).classLoaderScope)
            }

            val syntheticProject = ProjectBuilder()
                .withParent(syntheticRootProject)
                .withProjectDir(baseAccessorsBuildDir / "kotlin-dsl-accessors/library")
                .build()

            val pluginRequests =
                PluginRequestCollector(StringScriptSource("script", "")).apply {
                    createSpec(1).apply {
                        pluginDependencies
                            .filter { it.accessors }
                            .forEach { id(it.id) }
                    }
                }.pluginRequests

            val targetProjectScope = (syntheticProject as ProjectInternal).classLoaderScope
            syntheticProject.serviceOf<PluginRequestApplicator>().apply {
                applyPlugins(
                    pluginRequests,
                    syntheticProject.buildscript as ScriptHandlerInternal,
                    syntheticProject.pluginManager as PluginManagerInternal,
                    targetProjectScope)
            }

            val accessorsClassPath = accessorsClassPathFor(
                syntheticProject,
                syntheticProject.serviceOf<KotlinScriptClassPathProvider>().compilationClassPathOf(targetProjectScope))
            val sourceFile =
                accessorsClassPath.src.asFiles.asSequence().flatMap { it.walkTopDown().filter { it.isFile } }.single()
            generatedAccessorsFile.writeText(
                relocateAccessors(sourceFile.readText()))
        }
    }

    val generateScriptPluginWrappers by creating {
        inputs.files(scriptPluginFiles)
        inputs.file(generatedAccessorsFile)
        outputs.dir(generatedSourcesDir)
        dependsOn(generateAccessors)
        doLast {
            for (scriptPlugin in scriptPlugins) {
                scriptPlugin.writeScriptPluginWrapperTo(generatedSourcesDir)
            }
            generatedAccessorsFile.copyTo(
                generatedSourcesDir / "org/gradle/kotlin/dsl/accessors/accessors.kt",
                overwrite = true)
        }
    }

    val kotlinDslImplicitImportsFile = File(buildDir, "kotlin-dsl-implicit-imports")
    val kotlinDslImplicitImports by creating {
        outputs.file(kotlinDslImplicitImportsFile)
        doLast {
            val implicitImports = project.serviceOf<ImplicitImports>().list
            kotlinDslImplicitImportsFile
                .writeText(
                    implicitImports.joinToString(separator = "\n", prefix = "org.gradle.kotlin.dsl.accessors.*\n"))
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


/**
 * Hack to avoid conflict with accessors generated for build scripts.
 */
fun relocateAccessors(text: String): String = text
    .replace(
        "package org.gradle.kotlin.dsl",
        "package org.gradle.kotlin.dsl.accessors")
    .replace("^fun ".toRegex(RegexOption.MULTILINE), "internal fun ")
    .replace("^val ".toRegex(RegexOption.MULTILINE), "internal val ")


fun DependencyHandler.plugin(id: String, version: String? = null) =
    create(
        group = id,
        name = id + ArtifactRepositoriesPluginResolver.PLUGIN_MARKER_SUFFIX,
        version = version)


/**
 * Declared `lateinit` so it can be used before its initialization otherwise
 * the declaration would have to be moved to before the
 * `dependencies { plugins { ... } }` block.
 */
lateinit var pluginDependencies: MutableList<ExtendedPluginDependencySpec>


fun DependencyHandler.plugins(spec: PluginDependenciesSpec.() -> Unit): Unit =
    spec(object : PluginDependenciesSpec {
        override fun id(id: String): PluginDependencySpec =
            ExtendedPluginDependencySpec(id).also {
                if (!::pluginDependencies.isInitialized) pluginDependencies = ArrayList()
                pluginDependencies.add(it)
            }
    })


class ExtendedPluginDependencySpec(val id: String, var accessors: Boolean = true) : PluginDependencySpec {

    override fun version(version: String?): PluginDependencySpec = this

    override fun apply(apply: Boolean): PluginDependencySpec = this

    fun accessors(enable: Boolean) {
        accessors = enable
    }
}


/**
 * Enables or disables the generation of statically typed accessors for model elements
 * contributed by this plugin.
 */
infix fun PluginDependencySpec.accessors(enable: Boolean) =
    (this as ExtendedPluginDependencySpec).accessors(enable)


fun fileCollectionOf(files: Collection<File>, name: String): FileCollection =
    DefaultFileCollectionFactory().fixed(name, files)


operator fun File.div(child: String) = resolve(child)
