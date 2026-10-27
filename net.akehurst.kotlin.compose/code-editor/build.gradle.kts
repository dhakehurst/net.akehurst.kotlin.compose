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
        val jvmDemo by getting {
            dependencies {
                // OS specific desktop runtime is only needed to run the demos; applications choose their own
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

// Window demos (src/jvmDemo), e.g. ./gradlew :code-editor:runCodeEditorDemo
val demoCompilation = kotlin.jvm().compilations.getByName("demo")
mapOf(
    "CodeEditor" to "test.CodeEditorDemoKt",
).forEach { (name, demoMainClass) ->
    tasks.register<JavaExec>("run${name}Demo") {
        group = "demo"
        description = "Opens the $name demo window"
        mainClass.set(demoMainClass)
        classpath = files(demoCompilation.output.allOutputs, demoCompilation.runtimeDependencyFiles)
    }
}
