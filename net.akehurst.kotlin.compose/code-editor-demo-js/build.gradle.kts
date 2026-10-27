/**
 * Copyright (C) 2025 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Browser demo of the code editor.
// Run with ./gradlew :code-editor-demo-js:jsBrowserDevelopmentRun and open the URL it prints.
// The JVM and Wasm targets come from project-conventions; they have no sources here.

plugins {
    id("project-conventions")
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
}

// do not publish
tasks.withType<AbstractPublishToMaven> { onlyIf { false } }

kotlin {
    js {
        binaries.executable()
    }
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":code-editor"))
                implementation(libs.compose.ui)
                implementation(libs.compose.foundation)
                implementation(libs.compose.material3)
                implementation(libs.material.icons.core)
            }
        }
    }
}
