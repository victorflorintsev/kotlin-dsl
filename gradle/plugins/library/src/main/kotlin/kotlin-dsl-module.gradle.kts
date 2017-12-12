/**
 * Configures a Gradle Kotlin DSL module.
 *
 * The assembled jar will:
 *  - be named after `base.archivesBaseName`
 *  - include all sources
 */

import org.gradle.api.internal.initialization.DefaultClassLoaderScope

apply {
    plugin("kotlin-library")
    plugin("test-workers-memory-limits")
}


// include all sources
afterEvaluate {
    tasks {
        val mainSourceSet = java.sourceSets["main"]
        "jar"(Jar::class) {
            from(mainSourceSet.allSource)
            manifest.attributes.apply {
                put("Implementation-Title", "Gradle Kotlin DSL (${project.name})")
                put("Implementation-Version", version)
            }
        }
    }
}


// set the Gradle Test Kit user home into custom installation build dir
val kotlinDslDebugPropertyName = "org.gradle.kotlin.dsl.debug"

if (hasProperty(kotlinDslDebugPropertyName) && findProperty(kotlinDslDebugPropertyName) != "false") {
    tasks.withType<Test> {
        systemProperty(
            "org.gradle.testkit.dir",
            "${rootProject.buildDir}/custom/test-kit-user-home")
    }
}


// enable strict ClassLoader semantics
tasks.withType<Test> {
    systemProperty(DefaultClassLoaderScope.STRICT_MODE_PROPERTY, true)
}


