plugins {
    id("linecode.android")
    id("linecode.verify")
}

val enableLocalModel =
    (providers.gradleProperty("linecodeEnableLocalModel").orNull ?: "false").toBoolean()
val bundleLocalModel =
    (providers.gradleProperty("linecodeLocalModelBundle").orNull ?: enableLocalModel.toString())
        .toBoolean()
val gpuLocalModel =
    (providers.gradleProperty("linecodeLocalModelGpu").orNull ?: "true").toBoolean()
val llamaSourceDir =
    providers.gradleProperty("linecodeLlamaCppSourceDir").orNull
        ?: rootProject.file("third_party/llama-cpp").absolutePath
val llamaPrebuiltDir =
    providers.gradleProperty("linecodeLlamaPrebuiltDir").orNull
        ?: rootProject.file("third_party/llama-cpp/prebuilt/arm64-v8a").absolutePath

android {
    namespace = "cn.lineai.engine.local"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("boolean", "LOCAL_MODEL_ENABLED", enableLocalModel.toString())
        buildConfigField("boolean", "LOCAL_MODEL_GPU", gpuLocalModel.toString())
        if (enableLocalModel && bundleLocalModel) {
            ndkVersion = "29.0.14206865"
            ndk {
                abiFilters += "arm64-v8a"
            }
            externalNativeBuild {
                cmake {
                    arguments += listOf(
                        "-DANDROID_STL=c++_shared",
                        "-DLINCODE_LLAMA_CPP_DIR=$llamaSourceDir",
                        "-DLINCODE_LLAMA_PREBUILT_DIR=$llamaPrebuiltDir"
                    )
                    cppFlags += "-std=c++17"
                }
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    if (enableLocalModel && bundleLocalModel) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
    }
}
