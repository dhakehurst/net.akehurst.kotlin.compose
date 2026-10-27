plugins {
    id("project-conventions")
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(libs.compose.ui)
                api(libs.compose.foundation)
                api(libs.compose.material3)
                api(libs.material.icons.core)
                api(libs.material.icons.extended)
                api(libs.nak.kotlinx.collections)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
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