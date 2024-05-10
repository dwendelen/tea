plugins {
    alias(libs.plugins.kotlinJvm)
}

group = "se.daan"
version = "1.0.0"

dependencies {
    implementation(platform(libs.aws.bom))
    implementation(projects.shared)
    implementation(libs.logback)
    implementation(libs.aws.dynamodb)
    testImplementation(libs.kotlin.test.junit)
}