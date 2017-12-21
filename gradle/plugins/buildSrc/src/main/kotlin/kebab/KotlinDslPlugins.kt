package kebab

import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

import org.gradle.api.*
import org.gradle.api.artifacts.dsl.*
import org.gradle.api.file.*
import org.gradle.api.internal.artifacts.dependencies.DefaultSelfResolvingDependency
import org.gradle.api.internal.initialization.ScriptHandlerInternal
import org.gradle.api.internal.plugins.PluginManagerInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.file.*
import org.gradle.api.plugins.*
import org.gradle.api.tasks.*
import org.gradle.groovy.scripts.StringScriptSource
import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.plugin.use.*
import org.gradle.plugin.management.internal.DefaultPluginRequests
import org.gradle.plugin.use.internal.PluginRequestApplicator
import org.gradle.plugin.use.internal.PluginRequestCollector
import org.gradle.plugin.use.resolve.internal.ArtifactRepositoriesPluginResolver
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension
import org.gradle.testfixtures.ProjectBuilder

import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.accessors.accessorsClassPathFor
import org.gradle.kotlin.dsl.provider.KotlinScriptClassPathProvider
import org.gradle.kotlin.dsl.support.ImplicitImports
import org.gradle.kotlin.dsl.support.serviceOf

import java.io.File


open class KotlinDslPlugins : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {

        apply { // we expect `kotlin-dsl` to have been applied for now
            plugin("java-gradle-plugin")
        }

        val pluginDependencies = PluginDependencies()
        project.extensions.add("pluginDependencies", pluginDependencies)

        val scriptPluginFiles = fileTree("src/main/kotlin") {
            include("*.gradle.kts")
        }

        val scriptPlugins by lazy {
            scriptPluginFiles.map(::ScriptPlugin)
        }

        tasks {

            val inferGradlePluginDeclarations by creating {
                doLast {
                    configure<GradlePluginDevelopmentExtension> {
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
            the<JavaPluginConvention>().sourceSets["main"].withConvention(KotlinSourceSet::class) {
                kotlin.srcDir(generatedSourcesDir)
            }

            "clean"(Delete::class) {
                delete(generatedSourcesDir)
            }

            val baseAccessorsBuildDir = buildDir / "kotlin-dsl-accessors"
            val generatedAccessorsFile = baseAccessorsBuildDir / "accessors.kt"
            val compile = project.configurations["compile"]
            val generateAccessors by creating {
                inputs.files(compile)
                outputs.file(generatedAccessorsFile)
                doLast {
                    val classPath = compile.files

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
                        "-script-templates", "kebab.KotlinScriptPlugin",
                        // Propagate implicit imports and other settings
                        "-Xscript-resolver-environment=kotlinDslImplicitImportsFile=\"$kotlinDslImplicitImportsFile\"")
                }
            }
        }

        afterEvaluate {
            dependencies {
                pluginDependencies
                    .filter { it.version != null }
                    .forEach { "compile"(plugin(id = it.id, version = it.version)) }
            }
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


typealias PluginDependencies = ArrayList<ExtendedPluginDependencySpec>


fun Project.pluginDependencies(configure: PluginDependenciesSpec.() -> Unit) {
    val pluginDependencies = extensions.getByName<PluginDependencies>("pluginDependencies")
    configure(
        PluginDependenciesSpec { id ->
            ExtendedPluginDependencySpec(id).also {
                pluginDependencies.add(it)
            }
        })
}


class ExtendedPluginDependencySpec(val id: String) : PluginDependencySpec {

    var accessors: Boolean = true

    var version: String? = null

    override fun version(version: String?): PluginDependencySpec {
        this.version = version
        return this
    }

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


/**
 * `plugin` dependency notation.
 *
 * For example:
 *
 * ```
 * dependencies {
 *     compile(plugin("org.jetbrains.kotlin.jvm", version = embeddedKotlinVersion))
 * }
 * ```
 */
fun DependencyHandler.plugin(id: String, version: String? = null) =
    create(
        group = id,
        name = id + ArtifactRepositoriesPluginResolver.PLUGIN_MARKER_SUFFIX,
        version = version)


fun fileCollectionOf(files: Collection<File>, name: String): FileCollection =
    DefaultFileCollectionFactory().fixed(name, files)


operator fun File.div(child: String) = resolve(child)
