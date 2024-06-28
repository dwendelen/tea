import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    js {
        moduleName = "tea"
        browser {
            commonWebpackConfig {
                outputFileName = "tea.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    static = (static ?: mutableListOf()).apply {
                        // Serve sources to debug inside browser
                        add(project.projectDir.path)
                        add(project.projectDir.resolve("src/jsMain/local").path)
                    }
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared)
        }
        jsMain.dependencies {
            implementation(npm("chart.js", "~3.9.1", generateExternals = true))
        }
    }
}
