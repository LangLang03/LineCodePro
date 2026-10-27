package cn.lineai.build

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.JavaVersion
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

/**
 * Pure JVM module convention: no Android, Java 11 bytecode, JUnit 4 on the test classpath.
 */
class LineCodeJavaConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("java-library")

            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = JavaVersion.VERSION_11
                targetCompatibility = JavaVersion.VERSION_11
            }

            tasks.withType<JavaCompile>().configureEach {
                options.encoding = "UTF-8"
            }

            dependencies.add("testImplementation", "junit:junit:4.13.2")
        }
    }
}
