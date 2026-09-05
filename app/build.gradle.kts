import java.util.Properties
import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.android.application)
}

val releaseVersionName = "1.2.8-max"
val releaseApkName = "LineCode Pro $releaseVersionName.APK"
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
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/java.util=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
        }
    }

    namespace = "cn.lineai"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "cn.lineai"
        minSdk = 26
        targetSdk = 37
        versionCode = 32
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
                storeFile = file(releaseSigningProperties.getProperty("storeFile"))
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
        create("debugUserCert") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
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

abstract class GenerateLicenseAssetTask : org.gradle.api.DefaultTask() {
    @get:org.gradle.api.tasks.InputFile
    abstract val licenseFile: org.gradle.api.file.RegularFileProperty

    @get:org.gradle.api.tasks.OutputDirectory
    abstract val outputDir: org.gradle.api.file.DirectoryProperty

    @org.gradle.api.tasks.TaskAction
    fun generate() {
        val target = outputDir.get().asFile.resolve("LICENSE")
        target.parentFile.mkdirs()
        licenseFile.get().asFile.copyTo(target, overwrite = true)
    }
}

val generateLicenseAsset = tasks.register<GenerateLicenseAssetTask>("generateLicenseAsset") {
    licenseFile.set(rootProject.file("LICENSE"))
    outputDir.set(layout.buildDirectory.dir("generated/license-assets"))
}

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            generateLicenseAsset,
            GenerateLicenseAssetTask::outputDir
        )
    }
}

val exportDebugUserCertApk by tasks.registering(Copy::class) {
    dependsOn("assembleDebugUserCert")
    from(layout.buildDirectory.dir("outputs/apk/debugUserCert"))
    include("*.apk")
    into(layout.buildDirectory.dir("outputs/apk/debugUserCert/export"))
    rename { "LineCode-user-cert-debug.apk" }
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
    it.name == "assembleRelease"
}.configureEach {
    finalizedBy(exportReleaseApk)
}

dependencies {
    implementation(project(":core-model"))
    implementation(project(":core-api"))
    implementation(project(":core-security"))
    implementation(project(":ipc"))
    implementation(project(":data"))
    implementation(project(":feature-ssh"))
    implementation(project(":feature-share"))
    implementation(project(":feature-tool"))
    implementation(project(":feature-model"))
    implementation(project(":ui-theme"))
    implementation(project(":markdown"))
    implementation(project(":tool-ui"))
    testImplementation(libs.robolectric)
    testImplementation(libs.junit)
    testImplementation(libs.json)
}
