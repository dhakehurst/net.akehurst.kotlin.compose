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

package net.akehurst.kotlin.compose.components.table

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** The rowModifier of tableHeader and tableFooter is applied to the header and footer rows. */
@OptIn(ExperimentalTestApi::class)
class test_TableRowModifier {

    @Test
    fun header_and_footer_rowModifier_are_applied() = runComposeUiTest {
        setContent {
            TableView(
                state = TableState(),
                tableModifier = Modifier.width(400.dp),
                headerModifier = Modifier.fillMaxWidth(),
                footerModifier = Modifier.fillMaxWidth(),
                headerContent = {
                    tableHeader(rowModifier = Modifier.testTag("headerRow")) {
                        tableHeaderCell(0, boxModifier = Modifier.weight(1f)) { Text("A") }
                        tableHeaderCell(1, boxModifier = Modifier.weight(1f)) { Text("B") }
                    }
                },
                footerContent = {
                    tableFooter(rowModifier = Modifier.testTag("footerRow")) {
                        tableFooterCell { Text("footer") }
                    }
                },
            ) {
                tableRow { tableCell(0) { Text("1") }; tableCell(1) { Text("2") } }
            }
        }
        onNodeWithTag("headerRow").assertIsDisplayed()
        onNodeWithTag("footerRow").assertIsDisplayed()
        // weighted header cells still share the full width of the header
        val headerWidth = onNodeWithTag("headerRow").fetchSemanticsNode().size.width
        val expected = with(density) { 400.dp.roundToPx() }
        assertEquals(expected, headerWidth)
    }
}
