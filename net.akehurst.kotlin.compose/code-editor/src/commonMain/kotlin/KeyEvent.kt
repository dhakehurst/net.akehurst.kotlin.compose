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

package net.akehurst.kotlin.compose.editor

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.utf16CodePoint

val KeyEvent.isCtrlEnter get() = (key == Key.Enter || utf16CodePoint == '\n'.code) && isCtrlPressed
val KeyEvent.isCtrlSpace get() = (key == Key.Spacebar || utf16CodePoint == ' '.code) && isCtrlPressed
val KeyEvent.isUndo get() = (key == Key.Z || utf16CodePoint == 'z'.code) && isCtrlPressed
val KeyEvent.isRedo get() = (key == Key.Z || utf16CodePoint == 'z'.code) && isCtrlPressed && isShiftPressed
val KeyEvent.isCtrlF get() = key == Key.F && (isCtrlPressed || isMetaPressed)
val KeyEvent.isCtrlR get() = key == Key.R && (isCtrlPressed || isMetaPressed)
val KeyEvent.isEscape get() = key == Key.Escape
val KeyEvent.isEnter get() = key == Key.Enter && !isShiftPressed && !isCtrlPressed && !isMetaPressed
val KeyEvent.isShiftEnter get() = key == Key.Enter && isShiftPressed
val KeyEvent.isTab get() = key == Key.Tab && !isShiftPressed && !isCtrlPressed
val KeyEvent.isShiftTab get() = key == Key.Tab && isShiftPressed && !isCtrlPressed
