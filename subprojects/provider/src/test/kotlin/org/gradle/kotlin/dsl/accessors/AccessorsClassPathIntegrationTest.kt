/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.kotlin.dsl.accessors

import org.gradle.kotlin.dsl.fixtures.AbstractIntegrationTest
import org.gradle.kotlin.dsl.fixtures.fileByName
import org.gradle.kotlin.dsl.fixtures.matching

import org.gradle.kotlin.dsl.integration.kotlinBuildScriptModelFor

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.hasItem
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

import java.io.File


class AccessorsClassPathIntegrationTest : AbstractIntegrationTest() {

    @Test
    fun `classpath model includes generated accessors`() {

        val buildFile = withBuildScript("""
            plugins { java }
        """)

        println(
            build("kotlinDslAccessorsSnapshot").output)

        assertAccessorsInClassPathOf(buildFile)
    }

    @Test
    fun `classpath model includes jit accessors by default`() {

        val buildFile = withBuildScript("""
            plugins { java }
        """)

        assertAccessorsInClassPathOf(buildFile)
    }

    @Test
    fun `jit accessors can be turned off`() {

        val buildFile = withBuildScript("""
            plugins { java }
        """)

        withFile("gradle.properties", "org.gradle.kotlin.dsl.accessors=off")

        assertThat(
            classPathFor(buildFile),
            not(hasAccessorsJar()))
    }

    @Test
    fun `the set of jit accessors is a function of the set of applied plugins`() {

        val s1 = setOfAutomaticAccessorsFor(setOf("application"))
        val s2 = setOfAutomaticAccessorsFor(setOf("java"))
        val s3 = setOfAutomaticAccessorsFor(setOf("application"))
        val s4 = setOfAutomaticAccessorsFor(setOf("application", "java"))
        val s5 = setOfAutomaticAccessorsFor(setOf("java"))

        assertThat(s1, not(equalTo(s2))) // application ≠ java
        assertThat(s1, equalTo(s3))      // application = application
        assertThat(s2, equalTo(s5))      // java        = java
        assertThat(s1, equalTo(s4))      // application ⊇ java
    }

    private
    fun setOfAutomaticAccessorsFor(plugins: Set<String>): File {
        val script = "plugins {\n${plugins.joinToString(separator = "\n")}\n}"
        val buildFile = withBuildScript(script, produceFile = ::newOrExisting)
        return accessorsJarFor(buildFile)!!.relativeTo(buildFile.parentFile)
    }

    private
    fun assertAccessorsInClassPathOf(buildFile: File) {
        val model = kotlinBuildScriptModelFor(buildFile)
        assertThat(model.classPath, hasAccessorsJar())
        assertThat(model.sourcePath, hasAccessorsSource())
    }

    private
    fun hasAccessorsSource() =
        hasItem(
            matching<File>({ appendText("accessors source") }) {
                File(this, "org/gradle/kotlin/dsl/ConfigurationAccessors.kt").isFile
            })

    private
    fun hasAccessorsJar() =
        hasItem(fileByName(accessorsJarFileName))

    private
    fun accessorsJarFor(buildFile: File) =
        classPathFor(buildFile)
            .find { it.isFile && it.name == accessorsJarFileName }

    private
    val accessorsJarFileName = "gradle-kotlin-dsl-accessors.jar"

    private
    fun classPathFor(buildFile: File) =
        kotlinBuildScriptModelFor(buildFile).classPath

    private
    fun kotlinBuildScriptModelFor(buildFile: File) =
        kotlinBuildScriptModelFor(projectRoot, buildFile)
}
