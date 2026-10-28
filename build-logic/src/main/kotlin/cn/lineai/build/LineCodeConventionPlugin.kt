package cn.lineai.build

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.JavaVersion
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

class LineCodeConventionPlugin : Plugin<Project> {
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
                    xmlReport = true
                    htmlReport = true
                }
            }

            configurations.matching {
                it.name.endsWith("RuntimeClasspath")
            }.configureEach {
                exclude(mapOf("group" to "org.jetbrains.kotlin", "module" to "kotlin-stdlib"))
                exclude(mapOf("group" to "org.jetbrains.kotlin", "module" to "kotlin-stdlib-common"))
                exclude(mapOf("group" to "org.jetbrains.kotlin", "module" to "kotlin-stdlib-jdk7"))
                exclude(mapOf("group" to "org.jetbrains.kotlin", "module" to "kotlin-stdlib-jdk8"))
                exclude(mapOf("group" to "org.jetbrains", "module" to "annotations"))
            }

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
            dependencies {
                add("testImplementation", libs.findLibrary("junit").get())
            }
        }
    }
}
