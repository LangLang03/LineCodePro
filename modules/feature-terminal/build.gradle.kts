plugins {
    id("linecode.android")
    id("linecode.verify")
}

android {
    namespace = "cn.lineai.feature.terminal"
}

dependencies {
    implementation(project(":core-model"))
    implementation(project(":core-kernel"))
    implementation(project(":core-service"))
    implementation(project(":data"))
    api("com.github.mwiede:jsch:2.28.2")
}
