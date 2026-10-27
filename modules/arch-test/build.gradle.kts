plugins {
    id("linecode.java")
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    systemProperty("linecode.arch.root", rootProject.projectDir.absolutePath)
    // These tests inspect other modules, including the existence of empty source directories.
    // Always scan the live tree rather than reuse a result for only arch-test's own sources.
    outputs.upToDateWhen { false }
    outputs.doNotCacheIf("Architecture checks must validate the live repository tree") { true }
}
