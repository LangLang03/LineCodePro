plugins {
    id("linecode.android")
    id("linecode.verify")
}

android {
    namespace = "cn.lineai.engine.prompt"
}

dependencies {
    implementation(project(":core-model"))
    implementation(project(":core-kernel"))
    testImplementation("org.robolectric:robolectric:4.16")
}

android.testOptions.unitTests.isIncludeAndroidResources = true
