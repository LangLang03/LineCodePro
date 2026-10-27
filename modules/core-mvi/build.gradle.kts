plugins {
    id("linecode.java")
    id("linecode.verify")
}

dependencies {
    implementation(project(":core-model"))
    implementation(project(":core-kernel"))
    testImplementation("junit:junit:4.13.2")
}
