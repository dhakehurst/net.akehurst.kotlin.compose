plugins {
    id("project-conventions")
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":code-editor-api"))
                api(project(":components")) //for mutableStateFlowHolder
                api(libs.compose.ui)
                api(libs.compose.foundation)
                api(libs.compose.material3)
                api(libs.material.icons.core)
            }
        }
        jvmMain {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }
}