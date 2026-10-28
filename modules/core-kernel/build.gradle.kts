plugins {
    id("linecode.java")
    id("linecode.verify")
}

dependencies {
    implementation(project(":core-model"))
    testImplementation("junit:junit:4.13.2")
}
