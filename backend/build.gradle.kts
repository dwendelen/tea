plugins {
    alias(libs.plugins.kotlinJvm)
}

group = "se.daan"
version = "1.0.0"

dependencies {
    implementation(projects.shared)
    implementation(libs.logback)
    testImplementation(libs.kotlin.test.junit)
}