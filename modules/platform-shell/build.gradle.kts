plugins {
    id("linecode.android")
    id("linecode.verify")
}

android {
    namespace = "cn.lineai.shell"
}

dependencies {
    implementation(project(":core-model"))
    implementation(project(":core-kernel"))
    implementation(project(":data"))
    implementation(project(":tool-api"))
    // The single sanctioned rewritten->legacy edge: the gateway binds the AIDL provider.
    implementation(project(":ipc"))
    implementation("com.github.mwiede:jsch:2.28.2")
}
