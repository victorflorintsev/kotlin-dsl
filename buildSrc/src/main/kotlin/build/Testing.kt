package build

import org.gradle.api.Project
import org.gradle.api.internal.initialization.DefaultClassLoaderScope
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat


fun Project.withTestStrictClassLoading() {
    tasks.withType(Test::class.java) { test ->
        test.systemProperty(DefaultClassLoaderScope.STRICT_MODE_PROPERTY, true)
    }
}


fun Project.withTestWorkersMemoryLimits(min: String = "64m", max: String = "128m") {
    tasks.withType(Test::class.java) { test ->
        test.jvmArgs("-Xms$min", "-Xmx$max")
    }
}



