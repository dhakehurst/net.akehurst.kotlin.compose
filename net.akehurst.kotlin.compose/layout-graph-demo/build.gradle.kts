plugins {
    id("project-conventions")
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
}

// do not publish
tasks.withType<AbstractPublishToMaven> { onlyIf { false } }

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":layout-graph"))
                implementation(compose.ui)
                implementation(compose.foundation)
                implementation(compose.material3)
            }
        }
        jvmMain {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "net.akehurst.kotlin.components.layout.graph.demo.MainKt"
    }
}
