plugins {
    id("linecode.android")
    id("linecode.verify")
}

android {
    namespace = "cn.lineai.ui.md"
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    implementation(project(":core-model"))
    implementation(project(":ui-tokens"))
    implementation(project(":ui-kit"))
    implementation(project(":ui-i18n"))
    implementation("org.commonmark:commonmark:0.28.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.28.0")
    testImplementation("org.robolectric:robolectric:4.16")
}
