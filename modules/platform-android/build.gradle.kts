plugins {
    id("linecode.android")
    id("linecode.verify")
}

android {
    namespace = "cn.lineai.platform"
}

dependencies {
    implementation(project(":core-model"))
    implementation(project(":core-kernel"))
    implementation(project(":ui-i18n"))
    implementation(project(":engine-prompt"))
    implementation(project(":data"))
}
