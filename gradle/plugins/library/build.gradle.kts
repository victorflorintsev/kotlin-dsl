import kebab.*

plugins {
    `kotlin-dsl`
    id("kotlin-dsl-plugins")
}

// Express global plugin dependencies using a superset of the plugins DSL
pluginDependencies {
    kotlin("jvm") version embeddedKotlinVersion
    id("com.jfrog.artifactory") version "4.1.1" accessors false // opt-out of static accessors for elements contributed by this plugin
    `maven-publish`
}

dependencies {

    compile(project(":runtime"))

    testCompile("junit:junit:4.11")
    testCompile("com.nhaarman:mockito-kotlin:1.5.0")
}

repositories {
    gradlePluginPortal()
}
