
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

tasks.withType<Test> {
    testLogging {
        events("failed")
        exceptionFormat = TestExceptionFormat.FULL
    }
    val maxWorkerCount = gradle.startParameter.maxWorkerCount
    maxParallelForks = if (maxWorkerCount < 2) 1 else maxWorkerCount / 2
    logger.info("$path will run with maxParallelForks=$maxParallelForks.")
}
