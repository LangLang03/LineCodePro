plugins {
    id("linecode.android")
    id("linecode.verify")
}

android {
    namespace = "cn.lineai.feature.chat"
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
    implementation(project(":engine-agent"))
    implementation(project(":engine-ai"))
    implementation(project(":engine-prompt"))
    implementation(project(":engine-protocol"))
    implementation(project(":data"))
    implementation(project(":tool-api"))
    testImplementation("junit:junit:4.13.2")
}
