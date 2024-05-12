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
    implementation("com.amazonaws:aws-lambda-java-core:1.2.1")
    implementation("com.amazonaws:aws-lambda-java-events:3.11.0")
    testImplementation(libs.kotlin.test.junit)
}