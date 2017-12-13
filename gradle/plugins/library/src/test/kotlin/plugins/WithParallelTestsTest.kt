package plugins

import WithParallelTests

import com.nhaarman.mockito_kotlin.*

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.tasks.TaskContainer

import org.junit.Test

typealias TestTask = org.gradle.api.tasks.testing.Test

class WithParallelTestsTest {

    @Test
    fun `it configures all Test tasks`() {
        // given:
        val tasks = mock<TaskContainer>()
        val project = mock<Project> {
            on { getTasks() } doReturn tasks
        }

        // when:
        val plugin = WithParallelTests()
        plugin.apply(project)

        // then:
        verify(tasks).withType(
            eq(TestTask::class.java),
            any<Action<TestTask>>())
    }
}
