package cn.lineai.build

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.JavaVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.withType

/**
 * Android library convention for the rewritten modules.
 *
 * Keeps the runtime surface identical to the legacy modules (compileSdk 36, minSdk 26,
 * Java 11) but does not drag in the legacy dependency graph.
 */
class LineCodeAndroidConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")

            extensions.configure<LibraryExtension> {
                compileSdk = 36

                defaultConfig {
                    minSdk = 26
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_11
                    targetCompatibility = JavaVersion.VERSION_11
                }

                lint {
                    abortOnError = true
                    checkReleaseBuilds = true
                }
            }

            configurations.matching { it.name.endsWith("RuntimeClasspath") }.configureEach {
                exclude(mapOf("group" to "org.jetbrains.kotlin", "module" to "kotlin-stdlib"))
                exclude(mapOf("group" to "org.jetbrains.kotlin", "module" to "kotlin-stdlib-common"))
                exclude(mapOf("group" to "org.jetbrains.kotlin", "module" to "kotlin-stdlib-jdk7"))
                exclude(mapOf("group" to "org.jetbrains.kotlin", "module" to "kotlin-stdlib-jdk8"))
                exclude(mapOf("group" to "org.jetbrains", "module" to "annotations"))
            }

            tasks.withType<JavaCompile>().configureEach {
                options.encoding = "UTF-8"
            }

            dependencies.add("testImplementation", "junit:junit:4.13.2")
        }
    }
}
