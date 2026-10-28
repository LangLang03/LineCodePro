plugins {
    id("linecode.android")
    id("linecode.verify")
}

val enableLocalModel =
    (providers.gradleProperty("linecodeEnableLocalModel").orNull ?: "false").toBoolean()

android {
    namespace = "cn.lineai.feature.models"
}

androidComponents {
    onVariants(selector().all()) { variant ->
        if (enableLocalModel) {
            variant.sources.java?.addStaticSourceDirectory("src/local/java")
        }
    }
}

dependencies {
    implementation(project(":core-model"))
    implementation(project(":core-kernel"))
    implementation(project(":core-mvi"))
    implementation(project(":core-service"))
    implementation(project(":ui-tokens"))
    implementation(project(":ui-i18n"))
    implementation(project(":ui-kit"))
    implementation(project(":data"))
    implementation(project(":engine-ai"))
}
