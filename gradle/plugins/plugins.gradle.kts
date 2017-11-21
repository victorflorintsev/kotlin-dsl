import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

version = "1.0"

gradlePlugin {
    (plugins) {
        "kotlinLibrary" {
            id = "local-kotlin-library"
            implementationClass = "plugins.KotlinLibrary"
        }
        "kotlinDslModule" {
            id = "local-kotlin-dsl-module"
            implementationClass = "plugins.KotlinDslModule"
        }
        "publicKotlinDslModule" {
            id = "local-public-kotlin-dsl-module"
            implementationClass = "plugins.PublicKotlinDslModule"
        }
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf(
            "-Xjsr305=strict",
            "-Xskip-runtime-version-check")
    }
}

dependencies {
    compile(kotlin("gradle-plugin", version = "1.1.51"))
    compile(kotlin("reflect", version = "1.1.51"))
}

repositories {
    jcenter()
}
