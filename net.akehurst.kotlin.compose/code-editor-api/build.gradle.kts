plugins {
    id("project-conventions")
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
}

dependencies {
    commonMainApi(libs.compose.ui)
}