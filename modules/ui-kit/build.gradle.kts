plugins {
    id("linecode.android")
    id("linecode.verify")
}

android {
    namespace = "cn.lineai.ui.kit"
}

dependencies {
    implementation(project(":core-model"))
    implementation(project(":core-kernel"))
    implementation(project(":ui-tokens"))
    implementation(project(":ui-i18n"))
    implementation(project(":platform-android"))
}
