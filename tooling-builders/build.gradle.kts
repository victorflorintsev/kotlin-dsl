plugins {
    id("public-kotlin-dsl-module")
    id("with-parallel-tests")
    id("test-requires-custom-installation")
}

base {
    archivesBaseName = "gradle-kotlin-dsl-tooling-builders"
}

dependencies {
    compileOnly(gradleApi())

    compile(project(":provider"))

    testCompile(project(":test-fixtures"))
}
