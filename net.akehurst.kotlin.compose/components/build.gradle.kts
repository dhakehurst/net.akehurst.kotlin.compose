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
        val jvmDemo by getting {
            dependencies {
                // OS specific desktop runtime is only needed to run the demos; applications choose their own
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

// Window demos (src/jvmDemo), e.g. ./gradlew :components:runTableViewDemo
val demoCompilation = kotlin.jvm().compilations.getByName("demo")
mapOf(
    "TableView" to "net.akehurst.kotlin.compose.components.table.TableViewDemoKt",
    "TreeView" to "TreeViewDemoKt",
).forEach { (name, demoMainClass) ->
    tasks.register<JavaExec>("run${name}Demo") {
        group = "demo"
        description = "Opens the $name demo window"
        mainClass.set(demoMainClass)
        classpath = files(demoCompilation.output.allOutputs, demoCompilation.runtimeDependencyFiles)
    }
}
