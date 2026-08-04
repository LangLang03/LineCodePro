plugins {
    id("linecode.convention")
}

android {
    namespace = "cn.lineai.tool"
}

dependencies {
    implementation(project(":core-model"))
    implementation(project(":core-api"))
    implementation(project(":core-security"))
    implementation(project(":data"))
    implementation(project(":ipc"))
    implementation(project(":feature-ssh"))
    implementation(project(":tool-ui"))
    api(libs.json)
}
