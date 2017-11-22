import build.*
import tasks.GenerateKotlinDependencyExtensions

plugins {
    id("public-kotlin-dsl-module")
    id("with-parallel-tests")
}

base {
    archivesBaseName = "gradle-kotlin-dsl"
}

dependencies {
    compileOnly(gradleApi())

    compile(project(":tooling-models"))
    compile(futureKotlin("stdlib-jre8"))
    compile(futureKotlin("reflect"))
    compile(futureKotlin("compiler-embeddable"))
    compile(futureKotlin("sam-with-receiver-compiler-plugin")) {
        isTransitive = false
    }

    testCompile(project(":test-fixtures"))
}


// --- Enable automatic generation of API extensions -------------------
val apiExtensionsOutputDir = file("src/generated/kotlin")

java.sourceSets["main"].kotlin {
    srcDir(apiExtensionsOutputDir)
}

tasks {

    val generateKotlinDependencyExtensions by creating(GenerateKotlinDependencyExtensions::class) {
        val publishedPluginsVersion: String by rootProject.extra
        outputFile = File(apiExtensionsOutputDir, "org/gradle/kotlin/dsl/KotlinDependencyExtensions.kt")
        embeddedKotlinVersion = kotlinVersion
        kotlinDslPluginsVersion = publishedPluginsVersion
        kotlinDslRepository = kotlinRepo
    }

    "compileKotlin" {
        dependsOn(generateKotlinDependencyExtensions)
    }

    "clean"(Delete::class) {
        delete(apiExtensionsOutputDir)
    }

    val prepareIntegrationTestFixtures by rootProject.tasks
    val customInstallation by rootProject.tasks
    "test" {
        dependsOn(prepareIntegrationTestFixtures)
        dependsOn(customInstallation)
    }
}
