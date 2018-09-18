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

package org.gradle.kotlin.dsl.integration

import org.gradle.kotlin.dsl.fixtures.FoldersDsl
import org.gradle.kotlin.dsl.fixtures.FoldersDslExpression
import org.gradle.kotlin.dsl.fixtures.containsMultiLineString
import org.gradle.kotlin.dsl.fixtures.withFolders

import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

import java.io.File


class ProjectSchemaAccessorsIntegrationTest : AbstractPluginIntegrationTest() {

    @Test
    fun `can access extension of internal type made public`() {

        lateinit var extensionSourceFile: File

        withFolders {

            "buildSrc" {

                withPrecompiledPlugins()

                "src/main/kotlin" {

                    extensionSourceFile =
                        withFile("Extension.kt", """
                            internal
                            class Extension
                        """)

                    withFile("plugin.gradle.kts", """
                        extensions.add("extension", Extension())
                    """)
                }
            }
        }

        withBuildScript("""

            plugins { id("plugin") }

            inline fun <reified T> typeOf(value: T) = typeOf<T>()

            println("extension: " + typeOf(extension))
            extension { println("extension{} " + typeOf(this)) }
        """)

        // The internal Extension type is not accessible
        // so the accessors are typed Any (java.lang.Object)
        assertThat(
            build("help", "-q").output,
            containsMultiLineString("""
                extension: java.lang.Object
                extension{} java.lang.Object
            """)
        )

        // Making the Extension type accessible
        // should cause the accessors to be regenerated
        // with the now accessible type
        extensionSourceFile.writeText("""
            public
            class Extension
        """)

        assertThat(
            build("help", "-q").output,
            containsMultiLineString("""
                extension: Extension
                extension{} Extension
            """)
        )
    }

    @Test
    fun `can access extension of default package type`() {

        withFolders {

            "buildSrc" {

                withPrecompiledPlugins()

                "src/main/kotlin" {

                    withFile("Extension.kt", """
                        class Extension(private val name: String) : org.gradle.api.Named {
                            override fun getName() = name
                        }
                    """)

                    withFile("plugin.gradle.kts", """
                        extensions.add("extension", Extension("foo"))
                        extensions.add("beans", container(Extension::class))
                    """)
                }
            }
        }

        withBuildScript("""

            plugins { id("plugin") }

            inline fun <reified T> typeOf(value: T) = typeOf<T>()

            println("extension: " + typeOf(extension))
            extension { println("extension{} " + typeOf(this)) }

            println("beans: " + typeOf(beans))
            beans { println("beans{} " + typeOf(beans)) }
        """)

        assertThat(
            build("help", "-q").output,
            containsMultiLineString("""
                extension: Extension
                extension{} Extension
                beans: org.gradle.api.NamedDomainObjectContainer<Extension>
                beans{} org.gradle.api.NamedDomainObjectContainer<Extension>
            """)
        )
    }

    @Test
    fun `can access extension of nested type`() {

        withFolders {

            "buildSrc" {

                withPrecompiledPlugins()

                "src/main/kotlin/my" {

                    withFile("Extension.kt", """
                        package my

                        class Nested {
                            class Extension(private val name: String) : org.gradle.api.Named {
                                override fun getName() = name
                            }
                        }
                    """)

                    withFile("plugin.gradle.kts", """
                        package my

                        extensions.add("nested", Nested.Extension("foo"))
                        extensions.add("beans", container(Nested.Extension::class))
                    """)
                }
            }
        }


        withBuildScript("""

            plugins { id("my.plugin") }

            inline fun <reified T> typeOf(value: T) = typeOf<T>()

            println("nested: " + typeOf(nested))
            nested { println("nested{} " + typeOf(this)) }

            println("beans: " + typeOf(beans))
            beans { println("beans{} " + typeOf(beans)) }

            // ensure API usage
            println(beans.create("bar").name)
            beans.create("baz") { println(name) }
        """)

        assertThat(
            build("help", "-q").output,
            containsMultiLineString("""
                nested: my.Nested.Extension
                nested{} my.Nested.Extension
                beans: org.gradle.api.NamedDomainObjectContainer<my.Nested.Extension>
                beans{} org.gradle.api.NamedDomainObjectContainer<my.Nested.Extension>
                bar
                baz
            """)
        )
    }

    @Test
    fun `multiple generic extension targets`() {

        withFolders {

            "buildSrc" {

                withPrecompiledPlugins()

                "src/main/kotlin" {
                    withFile("types.kt", """

                        package my

                        data class NamedString(val name: String, var value: String? = null)

                        data class NamedLong(val name: String, var value: Long? = null)
                    """)

                    withFile("plugin.gradle.kts", """

                        package my

                        val strings = container(NamedString::class) { NamedString(it) }
                        extensions.add("strings", strings)

                        val longs = container(NamedLong::class) { NamedLong(it) }
                        extensions.add("longs", longs)

                        tasks.register("printStringsAndLongs") {
                            doLast {
                                strings.forEach { println("string: " + it) }
                                longs.forEach { println("long: " + it) }
                            }
                        }
                    """)
                }
            }
        }

        withBuildScript("""

            plugins { id("my.plugin") }

            strings.create("foo") { value = "bar" }
            longs.create("ltuae") { value = 42L }
        """)

        assertThat(
            build("printStringsAndLongs", "-q").output,
            containsMultiLineString("""
                string: NamedString(name=foo, value=bar)
                long: NamedLong(name=ltuae, value=42)
            """)
        )
    }

    @Test
    fun `conflicting extensions across build scripts with same body`() {

        withFolders {

            "buildSrc" {

                withPrecompiledPlugins()

                "src/main/kotlin" {
                    withFile("my/extensions.kt", """
                        package my
                        open class App { lateinit var name: String }
                        open class Lib { lateinit var name: String }
                    """)
                    withFile("app.gradle.kts", """
                        extensions.create("my", my.App::class)
                    """)
                    withFile("lib.gradle.kts", """
                        extensions.create("my", my.Lib::class)
                    """)
                }
            }

            fun FoldersDsl.withPlugin(plugin: String) =
                withFile("build.gradle.kts", """
                    plugins { id("$plugin") }
                    my { name = "kotlin-dsl" }
                """)

            "app" {
                withPlugin("app")
            }

            "lib" {
                withPlugin("lib")
            }

            withFile("settings.gradle.kts", """
                include("app", "lib")
            """)
        }

        build("tasks")
    }

    @Test
    fun `conflicting extensions across build runs`() {

        withFolders {

            "buildSrc" {

                withPrecompiledPlugins()

                "src/main/kotlin" {
                    withFile("my/extensions.kt", """
                        package my
                        open class App { lateinit var name: String }
                        open class Lib { lateinit var name: String }
                    """)
                    withFile("app-or-lib.gradle.kts", """
                        val my: String? by project
                        val extensionType = if (my == "app") my.App::class else my.Lib::class
                        extensions.create("my", extensionType)
                    """)
                }
            }

            withFile("build.gradle.kts", """
                plugins { id("app-or-lib") }
                my { name = "kotlin-dsl" }
            """)

            withDefaultSettings()
        }

        build("tasks", "-Pmy=lib")
        build("tasks", "-Pmy=app")
    }

    private
    fun FoldersDsl.withPrecompiledPlugins() {
        withFile("settings.gradle.kts", defaultSettingsScript)
        withFile("build.gradle.kts", """

            plugins {
                `kotlin-dsl`
                `kotlin-dsl-precompiled-script-plugins`
                `java-gradle-plugin`
            }

            $repositoriesBlock
        """)
    }

    @Test
    fun `can configure deferred configurable extension`() {

        withBuildScript("""

            import org.gradle.api.publish.maven.MavenPublication

            plugins {
                `java-library`
                `maven-publish`
            }

            dependencies {
                api("com.google.guava:guava:21.0")
            }

            publishing {
                publications.create<MavenPublication>("mavenJavaLibrary") {
                    from(components["java"])
                }
            }

            dependencies {
                api("org.apache.commons:commons-lang3:3.5")
            }

        """)

        build("generatePom")

        val pom = existing("build/publications/mavenJavaLibrary/pom-default.xml").readText()
        assertThat(pom, containsString("com.google.guava"))
        assertThat(pom, containsString("commons-lang3"))
    }

    @Test
    fun `can access NamedDomainObjectContainer extension via generated accessor`() {

        withKotlinBuildSrc()

        withFile("buildSrc/src/main/kotlin/my/DocumentationPlugin.kt", """
            package my

            import org.gradle.api.*
            import org.gradle.kotlin.dsl.*

            class DocumentationPlugin : Plugin<Project> {

                override fun apply(project: Project) {
                    val books = project.container(Book::class, ::Book)
                    project.extensions.add("the books", books)
                }
            }

            data class Book(val name: String)

        """)

        val buildFile = withBuildScript("""

            apply<my.DocumentationPlugin>()

        """)


        println(
            build("kotlinDslAccessorsSnapshot").output)


        buildFile.appendText("""

            (`the books`) {
                register("quickStart") {
                }
                register("userGuide") {
                }
            }

            tasks {
                register("books") {
                    doLast { println(`the books`.joinToString { it.name }) }
                }
            }

        """)
        assertThat(
            build("books").output,
            containsString("quickStart, userGuide"))
    }

    @Test
    fun `can access extensions registered by declared plugins via jit accessor`() {

        withBuildScript("""
            plugins { application }

            application { mainClassName = "App" }

            task("mainClassName") {
                doLast { println("*" + application.mainClassName + "*") }
            }
        """)

        assertThat(
            build("mainClassName").output,
            containsString("*App*"))
    }

    @Test
    fun `can access configurations registered by declared plugins via jit accessor`() {

        withSettings("""
            include("a", "b", "c")
        """)

        withBuildScriptIn("a", """
            plugins { `java-library` }
        """)

        withBuildScriptIn("b", """
            plugins { `java-library` }

            dependencies {
                compile("org.apache.commons:commons-io:1.3.2")
            }

            repositories { jcenter() }
        """)

        withBuildScriptIn("c", """
            plugins { `java-library` }

            dependencies {
                compileOnly(group = "org.slf4j", name = "slf4j-api", version = "1.7.25")
                api("com.google.guava:guava:21.0")
                implementation("ch.qos.logback:logback-classic:1.2.3") {
                    isTransitive = false
                }
                implementation(project(":a"))
                implementation(project(":b")) {
                    exclude(group = "org.apache.commons")
                }
            }

            repositories { jcenter() }

            configurations.compileClasspath.files.forEach {
                println(org.gradle.util.TextUtil.normaliseFileSeparators(it.path))
            }
        """)

        val result = build("help", "-q")

        assertThat(
            result.output,
            allOf(
                containsString("slf4j-api-1.7.25.jar"),
                containsString("guava-21.0.jar"),
                containsString("logback-classic-1.2.3.jar"),
                containsString("a/build/classes/java/main"),
                containsString("b/build/classes/java/main"),
                not(containsString("logback-core")),
                not(containsString("commons-io"))))
    }

    @Test
    fun `accessors tasks applied in a mixed Groovy-Kotlin multi-project build`() {

        withSettings("include(\"a\")")
        withBuildScriptIn("a", "")

        val aTasks = build(":a:tasks").output
        assertThat(aTasks, containsString("kotlinDslAccessorsReport"))
        assertThat(aTasks, not(containsString("kotlinDslAccessorsSnapshot")))

        val rootTasks = build(":tasks").output
        assertThat(rootTasks, allOf(containsString("kotlinDslAccessorsReport"), containsString("kotlinDslAccessorsSnapshot")))
    }

    @Test
    fun `given extension with inaccessible type, its accessor is typed Any`() {

        withFile("init.gradle", """
            initscript {
                repositories {
                    gradlePluginPortal()
                }
                dependencies {
                    classpath "com.gradle:build-scan-plugin:1.16"
                }
            }
            rootProject {
                apply plugin: "base"
                apply plugin: initscript.classLoader.loadClass("com.gradle.scan.plugin.BuildScanPlugin")
                buildScan {
                    publishAlways()
                }
            }
        """)

        withBuildScript("""

            inline fun <reified T> typeOf(t: T) = T::class.simpleName

            buildScan {
                println("Type of `buildScan` receiver is " + typeOf(this@buildScan))
            }
        """)

        val result = build("help", "-I", "init.gradle")
        assertThat(result.output, containsString("Type of `buildScan` receiver is Any"))
    }

    @Test
    fun `given extension with erased generic type parameters, its accessor is typed Any`() {

        withDefaultSettingsIn("buildSrc")

        withFile("buildSrc/build.gradle.kts", """
            plugins {
                `kotlin-dsl`
                `java-gradle-plugin`
            }

            gradlePlugin {
                plugins {
                    register("mine") {
                        id = "mine"
                        implementationClass = "foo.FooPlugin"
                    }
                }
            }

            $repositoriesBlock
        """)

        withFile("buildSrc/src/main/kotlin/foo/FooPlugin.kt", """
            package foo

            import org.gradle.api.*

            open class MyExtension<T>

            open class FooPlugin : Plugin<Project> {
                override fun apply(project: Project): Unit = project.run {
                    // Using add() without specifying the public type causes type erasure
                    extensions.add("mine", MyExtension<String>())
                }
            }
        """)

        withBuildScript("""
            plugins {
                id("mine")
            }

            inline fun <reified T> typeOf(t: T) = T::class.simpleName

            mine {
                println("Type of `mine` receiver is " + typeOf(this@mine))
            }
        """)

        val result = build("help")
        assertThat(result.output, containsString("Type of `mine` receiver is Any"))
    }

    @Test
    fun `can access nested extensions and conventions registered by declared plugins via jit accessors`() {

        withDefaultSettingsIn("buildSrc")

        withBuildScriptIn("buildSrc", """
            plugins {
                `java-gradle-plugin`
                `kotlin-dsl`
            }

            gradlePlugin {
                plugins {
                    register("my-plugin") {
                        id = "my-plugin"
                        implementationClass = "plugins.MyPlugin"
                    }
                }
            }

            $repositoriesBlock
        """)

        withFile("buildSrc/src/main/kotlin/plugins/MyPlugin.kt", """
            package plugins

            import org.gradle.api.*
            import org.gradle.api.plugins.*
            import org.gradle.api.internal.*
            import org.gradle.api.internal.plugins.*

            open class MyPlugin : Plugin<Project> {
                override fun apply(project: Project): Unit = project.run {

                    val rootExtension = MyExtension("root")
                    val rootExtensionNestedExtension = MyExtension("nested-in-extension")
                    val rootExtensionNestedConvention = MyConvention("nested-in-extension")

                    extensions.add("rootExtension", rootExtension)

                    rootExtension.extensions.add("nestedExtension", rootExtensionNestedExtension)
                    rootExtensionNestedExtension.extensions.add("deepExtension", listOf("foo", "bar"))

                    rootExtensionNestedConvention.extensions.add("deepExtension", mapOf("foo" to "bar"))

                    val rootConvention = MyConvention("root")
                    val rootConventionNestedExtension = MyExtension("nested-in-convention")
                    val rootConventionNestedConvention = MyConvention("nested-in-convention")

                    convention.plugins.put("rootConvention", rootConvention)

                    rootConvention.extensions.add("nestedExtension", rootConventionNestedExtension)
                    rootConventionNestedExtension.extensions.add("deepExtension", listOf("bazar", "cathedral"))

                    rootConventionNestedConvention.extensions.add("deepExtension", mapOf("bazar" to "cathedral"))
                }
            }

            class MyExtension(val value: String = "value") : ExtensionAware, HasConvention {
                private val convention: DefaultConvention = DefaultConvention()
                override fun getExtensions(): ExtensionContainer = convention
                override fun getConvention(): Convention = convention
            }

            class MyConvention(val value: String = "value") : ExtensionAware, HasConvention {
                private val convention: DefaultConvention = DefaultConvention()
                override fun getExtensions(): ExtensionContainer = convention
                override fun getConvention(): Convention = convention
            }
        """)

        withBuildScript("""
            plugins {
                id("my-plugin")
            }

            rootExtension {
                nestedExtension {
                    require(value == "nested-in-extension", { "rootExtension.nestedExtension" })
                    require(deepExtension == listOf("foo", "bar"), { "rootExtension.nestedExtension.deepExtension" })
                }
            }

            rootConvention {
                nestedExtension {
                    require(value == "nested-in-convention", { "rootConvention.nestedExtension" })
                    require(deepExtension == listOf("bazar", "cathedral"), { "rootConvention.nestedExtension.deepExtension" })
                }
            }
        """)

        build("help")
    }

    @Test
    fun `convention accessors honor HasPublicType`() {

        withDefaultSettingsIn("buildSrc")

        withBuildScriptIn("buildSrc", """
            plugins {
                `java-gradle-plugin`
                `kotlin-dsl`
            }

            gradlePlugin {
                plugins {
                    register("my-plugin") {
                        id = "my-plugin"
                        implementationClass = "plugins.MyPlugin"
                    }
                }
            }

            $repositoriesBlock
        """)

        withFile("buildSrc/src/main/kotlin/plugins/MyPlugin.kt", """
            package plugins

            import org.gradle.api.*
            import org.gradle.api.plugins.*

            open class MyPlugin : Plugin<Project> {
                override fun apply(project: Project): Unit = project.run {
                    convention.plugins.put("myConvention", MyPrivateConventionImpl())
                }
            }
        """)

        withFile("buildSrc/src/main/kotlin/plugins/MyConvention.kt", """
            package plugins

            interface MyConvention

            internal
            class MyPrivateConventionImpl : MyConvention
        """)

        withBuildScript("""
            plugins {
                id("my-plugin")
            }

            inline fun <reified T> typeOf(t: T) = T::class.simpleName

            myConvention {
                println("Type of `myConvention` receiver is " + typeOf(this@myConvention))
            }
        """)

        assertThat(
            build("help").output,
            containsString("Type of `myConvention` receiver is Any"))

        withFile("buildSrc/src/main/kotlin/plugins/MyConvention.kt", """
            package plugins

            import org.gradle.api.reflect.*
            import org.gradle.kotlin.dsl.*

            interface MyConvention

            internal
            class MyPrivateConventionImpl : MyConvention, HasPublicType {
                override fun getPublicType() = typeOf<MyConvention>()
            }
        """)

        assertThat(
            build("help").output,
            containsString("Type of `myConvention` receiver is MyConvention"))
    }

    @Test
    fun `existing tasks accessors`() {

        withBuildScript("""
            plugins {
                java
            }
            tasks.existing.compileJava {
                options.isWarnings = true
            }
            tasks {
                existing.test {
                    dependsOn(existing.compileJava)
                }
            }
        """)

        build("help")
    }

    private
    fun withFolders(folders: FoldersDslExpression) =
        projectRoot.withFolders(folders)
}
