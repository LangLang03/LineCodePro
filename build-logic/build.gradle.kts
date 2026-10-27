plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("linecodeConvention") {
            id = "linecode.convention"
            implementationClass = "cn.lineai.build.LineCodeConventionPlugin"
        }
        register("linecodeFeatureConvention") {
            id = "linecode.feature.convention"
            implementationClass = "cn.lineai.build.LineCodeFeatureConventionPlugin"
        }
        register("linecodeJava") {
            id = "linecode.java"
            implementationClass = "cn.lineai.build.LineCodeJavaConventionPlugin"
        }
        register("linecodeAndroid") {
            id = "linecode.android"
            implementationClass = "cn.lineai.build.LineCodeAndroidConventionPlugin"
        }
        register("linecodeVerify") {
            id = "linecode.verify"
            implementationClass = "cn.lineai.build.LineCodeVerifyPlugin"
        }
    }
}
