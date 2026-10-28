import java.util.Properties
import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.android.application)
}

val releaseVersionName = "1.0.0"
val releaseApkName = "LineCode Terminal Provider $releaseVersionName.APK"
val releaseIdsigName = "$releaseApkName.idsig"
val releaseSigningProperties = Properties()
val releaseSigningFile = rootProject.file("signing.properties")
val hasReleaseSigningFile = releaseSigningFile.exists()
if (hasReleaseSigningFile) {
    releaseSigningFile.inputStream().use { releaseSigningProperties.load(it) }
}
val requiredReleaseSigningProperties = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
val missingReleaseSigningProperties = requiredReleaseSigningProperties.filter {
    releaseSigningProperties.getProperty(it).isNullOrBlank()
}
val hasReleaseSigning = hasReleaseSigningFile && missingReleaseSigningProperties.isEmpty()

val generateReleaseObfuscationDictionary by tasks.registering {
    val outputFile = layout.buildDirectory.file("generated/r8/obfuscation-dictionary.txt")
    outputs.file(outputFile)
    outputs.upToDateWhen { false }

    doLast {
        val alphabet = "abcdefghijklmnopqrstuvwxyz"
        val reserved = setOf(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
            "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
            "finally", "float", "for", "goto", "if", "implements", "import", "instanceof",
            "int", "interface", "long", "native", "new", "package", "private", "protected",
            "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized",
            "this", "throw", "throws", "transient", "try", "void", "volatile", "while"
        )
        val names = ArrayList<String>(8192)

        fun appendNames(prefix: String, remaining: Int) {
            if (names.size >= 8192) {
                return
            }
            if (remaining == 0) {
                if (!reserved.contains(prefix)) {
                    names.add(prefix)
                }
                return
            }
            for (index in alphabet.indices) {
                appendNames(prefix + alphabet[index], remaining - 1)
                if (names.size >= 8192) {
                    return
                }
            }
        }

        var length = 1
        while (names.size < 8192) {
            appendNames("", length)
            length++
        }

        val file = outputFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(names.joinToString(System.lineSeparator()) + System.lineSeparator())
    }
}

val purgeReleaseSymbolFiles by tasks.registering(Delete::class) {
    delete(layout.buildDirectory.dir("outputs/mapping/release"))
    delete(layout.buildDirectory.dir("outputs/native-debug-symbols/release"))
}

val exportReleaseApk by tasks.registering(Copy::class) {
    from(layout.buildDirectory.dir("outputs/apk/release"))
    include("*.apk", "*.apk.idsig", "*.idsig")
    into(layout.projectDirectory.dir("release"))
    rename {
        if (it.endsWith(".idsig", ignoreCase = true)) {
            releaseIdsigName
        } else {
            releaseApkName
        }
    }
}

val validateReleaseSigning by tasks.registering {
    doLast {
        if (!hasReleaseSigningFile) {
            throw GradleException("Release signing.properties is required for release builds. Debug signing must not be used for release artifacts.")
        }
        if (missingReleaseSigningProperties.isNotEmpty()) {
            throw GradleException("Release signing.properties is missing: ${missingReleaseSigningProperties.joinToString(", ")}")
        }
    }
}

android {
    namespace = "cn.lineai.terminalprovider"
    compileSdk = 36

    defaultConfig {
        applicationId = "cn.lineai.terminalprovider"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = releaseVersionName
    }

    signingConfigs {
        getByName("debug") {
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
        create("lineAiRelease") {
            if (hasReleaseSigning) {
                val rawStoreFile = releaseSigningProperties.getProperty("storeFile")
                val appProject = rootProject.findProject("app")
                val appProjectDir = (appProject?.projectDir ?: rootProject.projectDir).absolutePath
                storeFile = file("$appProjectDir/$rawStoreFile")
                storePassword = releaseSigningProperties.getProperty("storePassword")
                keyAlias = releaseSigningProperties.getProperty("keyAlias")
                keyPassword = releaseSigningProperties.getProperty("keyPassword")
            }
            enableV1Signing = false
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("lineAiRelease")
            }
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                debugSymbolLevel = "NONE"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        aidl = true
    }
}

val exportDebugUserCertApk by tasks.registering(Copy::class) {
    dependsOn("assembleDebug")
    from(layout.buildDirectory.dir("outputs/apk/debug"))
    include("*.apk")
    into(layout.buildDirectory.dir("outputs/apk/debug/export"))
    rename { "TerminalProvider-user-cert-debug.apk" }
}

configurations.matching {
    it.name == "debugRuntimeClasspath" || it.name == "releaseRuntimeClasspath"
}.configureEach {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-common")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    exclude(group = "org.jetbrains", module = "annotations")
}

tasks.matching {
    it.name == "minifyReleaseWithR8" || it.name == "minifyReleaseWithProguard"
}.configureEach {
    dependsOn(generateReleaseObfuscationDictionary)
}

tasks.matching {
    it.name == "assembleRelease" || it.name == "bundleRelease"
}.configureEach {
    dependsOn(validateReleaseSigning)
}

tasks.matching {
    it.name == "assembleRelease" || it.name == "bundleRelease"
}.configureEach {
    finalizedBy(purgeReleaseSymbolFiles)
}

tasks.matching {
    it.name == "assembleRelease"
}.configureEach {
    finalizedBy(exportReleaseApk)
}

dependencies {
    implementation(project(":ipc"))
}
