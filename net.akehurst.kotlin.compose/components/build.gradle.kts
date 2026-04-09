plugins {
    id("project-conventions")
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(compose.ui)
                api(compose.foundation)
                api(compose.material3)
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
        jvmMain {
            dependencies {
                api(compose.desktop.currentOs)
            }
        }
    }
}