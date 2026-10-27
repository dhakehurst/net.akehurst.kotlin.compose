plugins {
    id("project-conventions")
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
}

kotlin {

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.nak.kotlinx.utils)

                implementation(libs.compose.ui)
                implementation(libs.compose.foundation)
                implementation(libs.compose.material3)
                implementation(libs.material.icons.core)

                implementation(project(":components"))
            }
        }
        jvmTest {
            dependencies {
                // OS specific desktop runtime is only needed to run the tests; applications choose their own
                implementation(compose.desktop.currentOs)
            }
        }
    }
}