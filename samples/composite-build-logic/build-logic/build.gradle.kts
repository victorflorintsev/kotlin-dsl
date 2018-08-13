
plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "0.10.0"
}

group = "org.gradle.kotlin.dsl.samples.composite-build-logic"
version = "1.0"

gradlePlugin {
    plugins {
        create("included-plugin") {
            id = "included-plugin"
            implementationClass = "included.SamplePlugin"
        }
    }
}

repositories {
    jcenter()
}

