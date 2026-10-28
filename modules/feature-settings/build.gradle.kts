plugins {
    id("linecode.android")
    id("linecode.verify")
}

android {
    namespace = "cn.lineai.feature.settings"
}

dependencies {
    implementation(project(":core-model"))
    implementation(project(":core-kernel"))
    implementation(project(":core-mvi"))
    implementation(project(":core-service"))
    implementation(project(":ui-tokens"))
    implementation(project(":ui-i18n"))
    implementation(project(":ui-kit"))
    implementation(project(":ui-markdown"))
    implementation(project(":engine-prompt"))
    implementation(project(":data"))
    testImplementation("org.robolectric:robolectric:4.16")
}

android.testOptions.unitTests.isIncludeAndroidResources = true
