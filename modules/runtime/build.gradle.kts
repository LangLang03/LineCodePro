plugins {
    id("linecode.android")
    id("linecode.verify")
}

val enableLocalModel =
    (providers.gradleProperty("linecodeEnableLocalModel").orNull ?: "false").toBoolean()

android {
    namespace = "cn.lineai.runtime"
    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
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
    implementation(project(":data"))
    implementation(project(":engine-ai"))
    implementation(project(":engine-agent"))
    implementation(project(":engine-protocol"))
    implementation(project(":engine-prompt"))
    if (enableLocalModel) implementation(project(":engine-local"))
    implementation(project(":feature-chat"))
    implementation(project(":feature-settings"))
    implementation(project(":feature-models"))
    implementation(project(":feature-extensions"))
    implementation(project(":feature-workspace"))
    implementation(project(":feature-terminal"))
    implementation(project(":ipc"))
    implementation(project(":platform-android"))
    implementation(project(":tool-api"))
    implementation(project(":tool-agent"))
    implementation(project(":tool-builtin"))
    implementation(project(":tool-mcp"))
    implementation(project(":tool-media"))
    implementation(project(":tool-phone"))
    implementation(project(":ui-i18n"))
    implementation(project(":ui-kit"))
    implementation(project(":ui-tokens"))
}
