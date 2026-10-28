pluginManagement {
    repositories {
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
        google()
        mavenCentral()
    }
}

rootProject.name = "LineCode"
includeBuild("build-logic")

/**
 * Rewritten modules live under `modules/`, one Gradle module per package root.
 * The frozen legacy modules were removed in M6; only `:ipc` and `:terminal-provider` remain.
 */
fun rewritten(name: String) {
    include(":$name")
    project(":$name").projectDir = file("modules/$name")
}

listOf(
    "core-model",
    "core-kernel",
    "core-mvi",
    "core-service",
    "engine-protocol",
    "engine-prompt",
    "engine-ai",
    "engine-agent",
    "tool-api",
    "tool-builtin",
    "tool-mcp",
    "tool-media",
    "tool-agent",
    "tool-phone",
    "engine-skillhub",
    "engine-local",
    "data",
    "ui-tokens",
    "ui-i18n",
    "ui-kit",
    "ui-markdown",
    "feature-chat",
    "feature-settings",
    "feature-models",
    "feature-extensions",
    "feature-workspace",
    "feature-terminal",
    "platform-android",
    "runtime",
    "arch-test"
).forEach(::rewritten)

include(":app")
include(":ipc")
include(":terminal-provider")
