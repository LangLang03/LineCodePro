package cn.lineai.build

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class LineCodeFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("linecode.convention")

            extensions.configure<LibraryExtension> {
                buildFeatures {
                    buildConfig = false
                    resValues = false
                }
            }
        }
    }
}
