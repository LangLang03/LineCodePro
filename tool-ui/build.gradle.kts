plugins {
    id("linecode.convention")
}

android {
    namespace = "cn.lineai.tool.ui"
}

dependencies {
    implementation(project(":core-model"))
    implementation(project(":core-api"))
    implementation(project(":ui-theme"))
    implementation(project(":markdown"))
    api(libs.json)
}
