plugins {
    id("linecode.java")
    id("linecode.verify")
}

dependencies {
    compileOnly("org.json:json:20180813")
    implementation(project(":core-model"))
    implementation(project(":tool-api"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20180813")
}
