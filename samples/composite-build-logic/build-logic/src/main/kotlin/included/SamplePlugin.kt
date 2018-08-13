package included

import org.gradle.api.*
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.*


class SamplePlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {
        tasks.register<SampleTask>("sample") {
            message.set("SampleTask executed!")
        }
    }
}


open class SampleTask : DefaultTask() {

    @Input
    val message = project.objects.property<String>()

    @TaskAction
    fun sample() {
        logger.lifecycle(message.get())
    }
}

