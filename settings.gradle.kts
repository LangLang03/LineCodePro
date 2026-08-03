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
include(":core-model")
include(":core-api")
include(":core-security")
include(":app")
include(":data")
include(":terminal-provider")
include(":ipc")
include(":feature-ssh")
include(":feature-share")
include(":feature-tool")
include(":feature-model")
include(":ui-theme")
include(":markdown")
include(":tool-ui")
