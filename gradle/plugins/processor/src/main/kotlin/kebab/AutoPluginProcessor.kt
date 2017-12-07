package kebab

import java.io.File
import javax.annotation.processing.*

import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror


@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedOptions("kebab.plugins.output.file")
class AutoPluginProcessor : AbstractProcessor() {

    override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)
        resetInferredPluginDeclarationsOutput()
    }

    override fun process(set: MutableSet<out TypeElement>, roundEnv: RoundEnvironment): Boolean {

        val rootElements = roundEnv.rootElements

        val compiledScripts = rootElements
            .filter { isKotlinBuildScript(it) }
            .map { toCompiledScriptPlugin(it) }

        val gradlePlugins = rootElements
            .filter { isGradleProjectPlugin(it) }
            .map { toBinaryPlugin(it) }

        writePluginsFor(compiledScripts)

        writeInferredPluginDeclarations(
            compiledScripts.asSequence() + gradlePlugins.asSequence())

        return false // process doesn't own any annotations
    }

    private
    fun writePluginsFor(compiledScripts: List<CompiledScriptPlugin>) {
        compiledScripts.forEach {
            writePluginForCompiledScript(
                it.compiledScriptTypeName,
                it.implementationClass)
        }
    }

    private
    fun resetInferredPluginDeclarationsOutput() {
        outputFileForInferredPluginDeclarations.writeText("")
    }

    private
    fun writeInferredPluginDeclarations(allPlugins: Sequence<PluginDeclaration>) {
        outputFileForInferredPluginDeclarations.appendText(
            allPlugins.joinToString(separator = "\n", postfix = "\n") {
                it.run { "$id=$implementationClass" }
            })
    }

    private
    fun isKotlinBuildScript(element: Element): Boolean = element.isSubtypeOf(kotlinBuildScriptType)

    private
    val kotlinBuildScriptType by lazy {
        typeMirrorFor("org.gradle.kotlin.dsl.KotlinBuildScript")
    }

    private
    fun isGradleProjectPlugin(element: Element): Boolean = element.isSubtypeOf(projectPluginType)

    private
    val projectPluginType by lazy {
        types.getDeclaredType(
            null,
            typeElementFor("org.gradle.api.Plugin"),
            typeMirrorFor("org.gradle.api.Project"))
    }

    private
    fun toCompiledScriptPlugin(element: Element): CompiledScriptPlugin {
        val compiledScriptTypeName = element.simpleName!!.toString()
        val snakeCaseScriptName = compiledScriptTypeName.removeSuffix("_gradle")
        val pluginImplementationClass = "plugins." + snakeCaseScriptName.snakeCaseToCamelCase()
        val pluginId = snakeCaseScriptName.snakeCaseToKebabCase()
        return CompiledScriptPlugin(pluginId, pluginImplementationClass, compiledScriptTypeName)
    }

    private
    fun toBinaryPlugin(pluginElement: Element): BinaryPlugin {
        val simpleTypeName = pluginElement.simpleName!!.toString()
        val pluginId = simpleTypeName.pascalCaseToKebabCase()
        val implementationClass = pluginElement.toString()
        return BinaryPlugin(pluginId, implementationClass)
    }

    interface PluginDeclaration {
        val id: String
        val implementationClass: String
    }

    private
    data class CompiledScriptPlugin(
        override val id: String,
        override val implementationClass: String,
        val compiledScriptTypeName: String) : PluginDeclaration

    private
    data class BinaryPlugin(
        override val id: String,
        override val implementationClass: String) : PluginDeclaration

    private
    fun writePluginForCompiledScript(compiledScriptTypeName: String, qualifiedPluginTypeName: String) {
        val (packageName, typeName) = splitQualifiedTypeName(qualifiedPluginTypeName)
        outputFileFor(packageName, typeName).writeText(
            """
                package $packageName

                import org.gradle.api.Plugin
                import org.gradle.api.Project

                class $typeName : Plugin<Project> {
                    override fun apply(target: Project) {
                        Class
                            .forName("$compiledScriptTypeName")
                            .getDeclaredConstructor(Project::class.java)
                            .newInstance(target)
                    }
                }
            """.replaceIndent())
    }

    private
    fun splitQualifiedTypeName(qualifiedTypeName: String): Pair<String, String> =
        qualifiedTypeName.lastIndexOf('.').let {
            require(it > 0) {
                "`$qualifiedTypeName` is not a fully qualified name."
            }
            qualifiedTypeName.take(it) to qualifiedTypeName.drop(it + 1)
        }

    private
    fun outputFileFor(packageName: String, typeName: String) =
        File(outputPackageDirFor(packageName), "$typeName.kt")

    private
    fun outputPackageDirFor(packageName: String) =
        File(kaptKotlinGeneratedDir, packageName.replace('.', File.separatorChar)).apply {
            mkdirs()
        }

    private
    val kaptKotlinGeneratedDir: String
        get() = requiredOption("kapt.kotlin.generated")

    private
    val outputFileForInferredPluginDeclarations
        get() = File(requiredOption("kebab.plugins.output.file"))

    private
    fun requiredOption(key: String) = processingEnv.options[key]!!

    private
    fun CharSequence.snakeCaseToCamelCase() =
        replace(snakeCaseWordBoundary) { it.value.drop(1).toUpperCase() }

    private
    val snakeCaseWordBoundary by lazy { "_[a-z]".toRegex() }

    private
    fun String.snakeCaseToKebabCase() =
        decapitalize().replace('_', '-')

    private
    fun String.pascalCaseToKebabCase() =
        decapitalize().replace(upperCaseLetters) { "-${it.value.toLowerCase()}" }

    private
    val upperCaseLetters = "\\p{Upper}".toRegex()

    private
    fun typeMirrorFor(typeName: String) = typeElementFor(typeName).asType()

    private
    fun typeElementFor(typeName: String) = processingEnv.elementUtils.getTypeElement(typeName)

    private
    fun Element.isSubtypeOf(superType: TypeMirror) = asType().isSubtypeOf(superType)

    private
    fun TypeMirror.isSubtypeOf(superType: TypeMirror) = types.isSubtype(this, superType)

    private
    val types
        get() = processingEnv.typeUtils
}
