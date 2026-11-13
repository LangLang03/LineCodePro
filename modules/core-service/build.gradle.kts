plugins {
    id("linecode.java")
    id("linecode.verify")
}

dependencies {
    api(project(":core-model"))
    api(project(":core-kernel"))
    api(project(":core-mvi"))
    testImplementation("junit:junit:4.13.2")
}
