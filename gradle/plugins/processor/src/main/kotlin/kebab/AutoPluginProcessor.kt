package kebab

import java.io.File

import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType


@SupportedAnnotationTypes(value = "*")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
class AutoPluginProcessor : AbstractProcessor() {

    override fun process(set: MutableSet<out TypeElement>, roundEnv: RoundEnvironment): Boolean {
        val compiledScripts = roundEnv.rootElements.filter { isKotlinBuildScript(it) }
        compiledScripts.forEach { compiledScript ->
            val scriptTypeName = compiledScript.simpleName!!
            val pluginTypeName = pluginTypeNameFromScriptTypeName(scriptTypeName)
            outputFileForPlugin(pluginTypeName).writeText(
                """
                    package plugins

                    import org.gradle.api.Plugin
                    import org.gradle.api.Project

                    class $pluginTypeName : Plugin<Project> {
                        override fun apply(target: Project) {
                            Class
                                .forName("$scriptTypeName")
                                .getDeclaredConstructor(Project::class.java)
                                .newInstance(target)
                        }
                    }
                """.replaceIndent())
        }
        return true
    }

    private
    fun outputFileForPlugin(pluginTypeName: String) =
        File(kaptKotlinGeneratedDir, "plugins/$pluginTypeName.kt").apply { parentFile.mkdirs() }

    private
    fun pluginTypeNameFromScriptTypeName(simpleName: CharSequence): String =
        simpleName.removeSuffix("_gradle").snakeCaseToCamelCase()

    private
    fun CharSequence.snakeCaseToCamelCase() =
        replace(snakeCaseWordBoundary) { it.value.drop(1).toUpperCase() }

    private
    val snakeCaseWordBoundary by lazy { "_[a-z]".toRegex() }

    private
    fun isKotlinBuildScript(element: Element): Boolean =
        element.superclass?.isA<DeclaredType>()?.simpleName?.contentEquals("KotlinBuildScript") == true

    private
    val DeclaredType.simpleName
        get() = asElement().simpleName

    private
    val Element.superclass
        get() = isA<TypeElement>()?.superclass

    private
    val kaptKotlinGeneratedDir
        get() = processingEnv.options["kapt.kotlin.generated"]
}


inline
fun <reified T> Any.isA(): T? = this as? T

