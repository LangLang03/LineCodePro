plugins {
    id("linecode.android")
    id("linecode.verify")
}

android {
    namespace = "cn.lineai.ui.tokens"
}

dependencies {
    implementation(project(":core-model"))
    implementation(project(":core-kernel"))
}
