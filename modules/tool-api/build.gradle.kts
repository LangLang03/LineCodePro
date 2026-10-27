plugins {
    id("linecode.java")
    id("linecode.verify")
}

dependencies {
    implementation(project(":core-model"))
    implementation(project(":core-kernel"))
    compileOnly("org.json:json:20180813")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20180813")
}
