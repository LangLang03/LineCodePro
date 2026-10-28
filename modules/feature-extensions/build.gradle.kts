plugins {
    id("linecode.android")
    id("linecode.verify")
}

android {
    namespace = "cn.lineai.feature.extensions"
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
    implementation(project(":data"))
    implementation(project(":tool-api"))
    implementation(project(":engine-ai"))
    implementation(project(":engine-skillhub"))
    testImplementation("junit:junit:4.13.2")
}
