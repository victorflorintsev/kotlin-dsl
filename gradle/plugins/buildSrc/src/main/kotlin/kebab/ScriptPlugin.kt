package kebab

import java.io.File

data class ScriptPlugin(val sourceFile: File) {

    val id by lazy {
        sourceFile.name.removeSuffix(".gradle.kts")
    }

    val compiledScriptTypeName by lazy {
        id.capitalize().replace('-', '_') + "_gradle"
    }

    val implementationClass by lazy {
        id.kebabCaseToPascalCase()
    }
}


fun ScriptPlugin.writeScriptPluginWrapperTo(outputDir: File) =
    File(outputDir, "$implementationClass.kt").writeText(
        """
            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class $implementationClass : Plugin<Project> {
                override fun apply(target: Project) {
                    Class
                        .forName("$compiledScriptTypeName")
                        .getDeclaredConstructor(Project::class.java)
                        .newInstance(target)
                }
            }
        """.replaceIndent())


fun CharSequence.kebabCaseToPascalCase() =
    kebabCaseToCamelCase().capitalize()


fun CharSequence.kebabCaseToCamelCase() =
    replace("-[a-z]".toRegex()) { it.value.drop(1).toUpperCase() }
