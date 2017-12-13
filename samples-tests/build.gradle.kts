plugins {
    id("kotlin-library")
    id("with-parallel-tests")
    id("with-test-workers-memory-limits")
    id("test-requires-custom-installation")
}

dependencies {
    compile(project(":test-fixtures"))
    compile("org.xmlunit:xmlunit-matchers:2.5.1")
}

tasks {
    "test" {
        inputs.dir("../samples")
    }
}
