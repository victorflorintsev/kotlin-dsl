import org.gradle.api.Project
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withConvention
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet


val SourceSet.kotlin: SourceDirectorySet
    get() = withConvention(KotlinSourceSet::class) { kotlin }


fun SourceSet.kotlin(action: SourceDirectorySet.() -> Unit) =
    kotlin.action()


internal
fun Project.publishing(action: PublishingExtension.() -> Unit) =
    configure(action)
