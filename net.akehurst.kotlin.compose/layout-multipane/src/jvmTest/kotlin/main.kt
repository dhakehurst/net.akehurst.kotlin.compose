package net.akehurst.kotlin.compose.layout.multipane

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.*
import androidx.compose.ui.window.singleWindowApplication
import net.akehurst.kotlin.compose.components.SplitOrientation
import kotlin.test.Test


class test_MultiPaneLayout {

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun main() {
        // initial layout
        val lay = layoutNode {
            split(orientation = SplitOrientation.Horizontal) {
                split(0.5f, orientation = SplitOrientation.Vertical) {
                    pane(1f, title = "Pane A") {
                        Text("Pane A content", style = MaterialTheme.typography.headlineLarge)
                    }

                    pane(1f, title = "Pane B") {
                        Text("Pane B content", style = MaterialTheme.typography.headlineLarge)
                    }
                }
                tabbed(0.5f) {
                    pane(title = "Tab 1") {
                        Text("Tab 1 content", style = MaterialTheme.typography.headlineLarge)
                    }
                    pane(title = "Tab 2") {
                        Text("Tab 2 content", style = MaterialTheme.typography.headlineLarge)
                    }
                }
                /*                    split(orientation =  SplitOrientation.Vertical) {
                                        child(1f) {
                                            pane(title = "Pane A") {
                                                Text("Pane A content", style = MaterialTheme.typography.headlineLarge)
                                            }
                                        }
                                        child(1f) {
                                            pane(title = "Pane B") {
                                                Text("Pane B content", style = MaterialTheme.typography.headlineLarge)
                                            }
                                        }
                                    }*/
            }
        }
        //println(lay.asString(""))
        val layoutState = MultiPaneLayoutState(
            lay,
            onLayoutChanged = {
                //println(it.asString(""))
            }
        )

        /*        val layoutState = MultiPaneLayoutState(
                    LayoutNode.Split(
                        orientation = SplitOrientation.Horizontal,
                        weights = listOf(0.4f, 0.6f), // Left half vs Right half
                        children = listOf(
                            LayoutNode.Split(
                                orientation = SplitOrientation.Vertical,
                                weights = listOf(0.5f, 0.5f),
                                children = listOf(
                                    mainPane,
                                    LayoutNode.Pane(title = "Pane B") { Text("Pane B content", style = MaterialTheme.typography.headlineLarge) }
                                ),
                            ),
                            LayoutNode.Split(
                                orientation = SplitOrientation.Vertical,
                                weights = listOf(0.7f, 0.3f),
                                children = listOf(
                                    LayoutNode.Pane(title = "Pane C") { Text("Pane C content", style = MaterialTheme.typography.headlineLarge) },
                                    LayoutNode.Pane(title = "Pane D") { Text("Pane D content", style = MaterialTheme.typography.headlineLarge) }
                                )
                            )
                        ),
                    )
                )*/

        singleWindowApplication(
            title = "Demo MultiPaneLayout",
        )
        {
            MultiPaneLayout(
                layoutState = layoutState,
                topRow = {
                    Row {
                        Button(
                            onClick = {

                                    layoutState.addPane(Pane(title = "New Pane") { Text("New Pane content", style = MaterialTheme.typography.headlineLarge) })

                            }
                        ) {
                            Text("Add Pane")
                        }
                    }
                }
            )
        }
    }
}


