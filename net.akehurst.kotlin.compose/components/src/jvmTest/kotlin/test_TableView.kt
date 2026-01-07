package net.akehurst.kotlin.compose.components.table

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.singleWindowApplication
import kotlin.test.Test


class test_TableView {
    @Test
    fun main() {
        singleWindowApplication(
            title = "Demo MultiPaneLayout",
        ) {
            val state = remember { TableState() }
            TableView(
                state,
                tableModifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = Color.Black)
                    .background(Color.Green),
                headerModifier =  Modifier
                    .fillMaxWidth(),
                headerContent = {
                    tableHeader(
                        rowModifier = Modifier
                            .border(width = 1.dp, color = Color.Black)
                            .background(Color.Red)
                    ) {
                        tableHeaderCell(0,
                            boxModifier = Modifier
                                .border(width = 1.dp, color = Color.Black)
                                .weight(0.1f)
                        ) { Text("Column 1") }
                        tableHeaderCell(1,
                            boxModifier = Modifier
                                .border(width = 1.dp, color = Color.Black)
                                .weight(0.1f)
                        ) { Text("Column 2") }
                    }
                }
            ){
                for(i in 1 .. 20) {
                    tableRow(
                        rowModifier = {Modifier
                            .background(if (i % 2 == 0) Color.White else Color.LightGray) }
                    ) {
                        tableCell(0,boxModifier = Modifier){Text("Item $i")}
                        tableCell(1,boxModifier = Modifier) {Text("Desc $i")}
                    }
                }
            }
        }
    }
}