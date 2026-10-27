plugins {
    id("linecode.android")
    id("linecode.verify")
}

android {
    namespace = "cn.lineai.data.v2"
}

dependencies {
    implementation(project(":core-model"))
    implementation(project(":core-kernel"))
    implementation(project(":core-service"))
    testImplementation("org.robolectric:robolectric:4.16")
}

android.testOptions.unitTests.isIncludeAndroidResources = true
