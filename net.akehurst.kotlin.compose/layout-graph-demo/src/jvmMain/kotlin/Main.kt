package net.akehurst.kotlin.components.layout.graph.demo

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Compound Layout Demo - Step 0"
    ) {
        MaterialTheme {
            DemoApp()
        }
    }
}


