plugins {
    id("project-conventions")
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
}

kotlin {

    sourceSets {
        commonMain {
            dependencies {
                api(libs.nak.kotlinx.utils) // api: LayoutNode.idGenerator is a UniqueIdentityGenerator
                implementation(libs.nak.kotlinx.logging.api)

                implementation(libs.compose.ui)
                implementation(libs.compose.foundation)
                implementation(libs.compose.material3)
                implementation(libs.material.icons.core)

                implementation(project(":components"))
            }
        }
        val jvmDemo by getting {
            dependencies {
                // OS specific desktop runtime is only needed to run the demos; applications choose their own
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

// Window demos (src/jvmDemo), e.g. ./gradlew :layout-multipane:runMultiPaneLayoutDemo
val demoCompilation = kotlin.jvm().compilations.getByName("demo")
mapOf(
    "MultiPaneLayout" to "net.akehurst.kotlin.compose.layout.multipane.MultiPaneLayoutDemoKt",
).forEach { (name, demoMainClass) ->
    tasks.register<JavaExec>("run${name}Demo") {
        group = "demo"
        description = "Opens the $name demo window"
        mainClass.set(demoMainClass)
        classpath = files(demoCompilation.output.allOutputs, demoCompilation.runtimeDependencyFiles)
    }
}
