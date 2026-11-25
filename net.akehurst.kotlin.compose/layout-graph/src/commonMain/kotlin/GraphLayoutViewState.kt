package net.akehurst.kotlin.compose.layout.graph

import androidx.compose.ui.geometry.Offset

/** Pan and zoom of a graph view: drawn at offset + zoom * position. */
data class GraphLayoutViewState(
    val zoom: Float = 1f,
    val offset: Offset = Offset.Zero
)
