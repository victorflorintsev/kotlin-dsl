import org.jetbrains.kotlin.gradle.dsl.Coroutines
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

apply { // still required as the plugins block is not handled during batch compilation
    plugin("kotlin")
}

kotlin {
    experimental.coroutines = Coroutines.ENABLE
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf(
            "-Xjsr305=strict",
            "-Xskip-runtime-version-check")
    }
}

// still required as accessors are not being generated
fun Project.kotlin(action: KotlinProjectExtension.() -> Unit) = configure(action)
