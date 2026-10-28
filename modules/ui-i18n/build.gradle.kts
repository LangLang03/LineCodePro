plugins {
    id("linecode.android")
    id("linecode.verify")
}

android {
    namespace = "cn.lineai.ui.i18n"
}

dependencies {
    implementation(project(":core-model"))
    implementation(project(":core-kernel"))
}
