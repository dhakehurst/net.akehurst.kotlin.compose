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

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue


/**
 * A dialog asking for a single line of text.
 * @param label optional label shown in the text field; no label when null
 */
@Composable
fun TextInputDialog(
    title: String,
    onDismissRequest: () -> Unit,
    onConfirmation: (String) -> Unit,
    label: String? = null,
) {
    // State to hold the text inside the TextField
    var inputText by remember { mutableStateOf("") }

    AlertDialog(
        title = { Text(text = title) },
        text = {
            // The text input field
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = label?.let { { Text(it) } },
                singleLine = true,
            )
        },
        onDismissRequest = { onDismissRequest() },
        confirmButton = { Button(onClick = { onConfirmation(inputText) }) { Text("Confirm") } },
        dismissButton = { Button(onClick = { onDismissRequest() }) { Text("Dismiss") } }
    )
}