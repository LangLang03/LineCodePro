plugins {
    id("linecode.java")
    id("linecode.verify")
}

dependencies {
    compileOnly("org.json:json:20180813")
    implementation(project(":core-model"))
    implementation(project(":core-kernel"))
    implementation(project(":engine-protocol"))
    implementation(project(":core-service"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20180813")
}
