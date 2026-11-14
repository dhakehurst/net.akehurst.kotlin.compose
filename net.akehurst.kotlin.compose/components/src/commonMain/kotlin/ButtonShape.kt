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

package net.akehurst.kotlin.compose.components

import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Shape

/**
 * The shape of the buttons that the components in this library draw themselves
 * (dialog OK/Cancel buttons, the Combo dropdown button, the code editor's find/replace buttons, ...).
 *
 * When null (the default), each component keeps its own default shape.
 * Provide a shape to set it once for all components, e.g. square corners:
 * ```
 * CompositionLocalProvider(LocalAkehurstButtonShape provides RectangleShape) { ... }
 * ```
 * A `buttonShape` argument passed to a component overrides this.
 */
val LocalAkehurstButtonShape: ProvidableCompositionLocal<Shape?> = compositionLocalOf { null }

/**
 * The button shape a component uses by default:
 * [LocalAkehurstButtonShape] when provided, otherwise [fallback].
 */
@Composable
@ReadOnlyComposable
fun akehurstButtonShape(fallback: Shape = ButtonDefaults.shape): Shape = LocalAkehurstButtonShape.current ?: fallback
