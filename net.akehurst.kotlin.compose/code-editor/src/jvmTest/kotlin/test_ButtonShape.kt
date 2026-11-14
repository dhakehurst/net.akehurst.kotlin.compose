/**
 * Copyright (C) 2026 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
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

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import net.akehurst.kotlin.compose.components.LocalAkehurstButtonShape
import net.akehurst.kotlin.compose.components.akehurstButtonShape
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class test_ButtonShape {

    @Test
    fun default_is_the_material3_button_shape() = runComposeUiTest {
        var expected: Shape? = null
        var actual: Shape? = null
        setContent {
            expected = ButtonDefaults.shape
            actual = akehurstButtonShape()
        }
        waitForIdle()
        assertEquals(expected, actual)
    }

    @Test
    fun default_uses_the_given_fallback() = runComposeUiTest {
        var expected: Shape? = null
        var actual: Shape? = null
        setContent {
            expected = MaterialTheme.shapes.small
            actual = akehurstButtonShape(MaterialTheme.shapes.small)
        }
        waitForIdle()
        assertEquals(expected, actual)
    }

    @Test
    fun provided_local_overrides_the_default() = runComposeUiTest {
        var actual: Shape? = null
        var actualWithFallback: Shape? = null
        setContent {
            CompositionLocalProvider(LocalAkehurstButtonShape provides RectangleShape) {
                actual = akehurstButtonShape()
                actualWithFallback = akehurstButtonShape(MaterialTheme.shapes.small)
            }
        }
        waitForIdle()
        assertEquals(RectangleShape, actual)
        assertEquals(RectangleShape, actualWithFallback)
    }
}
