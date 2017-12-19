package kebab

import org.gradle.kotlin.dsl.*

import org.gradle.api.Project

import org.gradle.plugin.use.PluginDependenciesSpec

import org.gradle.kotlin.dsl.resolver.KotlinBuildScriptDependenciesResolver
import org.gradle.plugin.use.PluginDependencySpec

import kotlin.script.extensions.SamWithReceiverAnnotations
import kotlin.script.templates.ScriptTemplateDefinition


/**
 * Script template definition for Kotlin script plugins.
 */
@ScriptTemplateDefinition(
    resolver = KotlinBuildScriptDependenciesResolver::class,
    scriptFilePattern = ".*\\.gradle\\.kts")
@SamWithReceiverAnnotations("org.gradle.api.HasImplicitReceiver")
@GradleDsl
abstract class KotlinScriptPlugin(project: Project) : Project by project {

    /**
     * Configures the build script classpath for this project.
     *
     * @see [Project.buildscript]
     */
    @Suppress("unused")
    open fun buildscript(@Suppress("unused_parameter") block: ScriptHandlerScope.() -> Unit) {
        throw IllegalStateException("The `buildscript` block is not supported on Kotlin script plugins, please use the `plugins` block or project level dependencies.")
    }

    /**
     * Configures the plugin dependencies for this project.
     *
     * @see [PluginDependenciesSpec]
     */
    @Suppress("unused")
    fun plugins(@Suppress("unused_parameter") block: PluginDependenciesSpec.() -> Unit) {
        block(
            PluginDependenciesSpec { pluginId ->
                project.pluginManager.apply(pluginId)
                NullPluginDependencySpec
            })
    }

    object NullPluginDependencySpec : PluginDependencySpec {
        override fun apply(apply: Boolean) = this
        override fun version(version: String?) = this
    }
}
