import org.gradle.api.Project
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.BasePluginConvention
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.tasks.SourceSet

import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

import org.gradle.kotlin.dsl.*


val SourceSet.kotlin: SourceDirectorySet
    get() = withConvention(KotlinSourceSet::class) { kotlin }


fun SourceSet.kotlin(action: SourceDirectorySet.() -> Unit) =
    kotlin.action()


internal
val Project.base
    get() = convention.getPlugin<BasePluginConvention>()


internal
val Project.java
    get() = convention.getPlugin<JavaPluginConvention>()


internal
fun Project.publishing(action: PublishingExtension.() -> Unit) =
    configure(action)
