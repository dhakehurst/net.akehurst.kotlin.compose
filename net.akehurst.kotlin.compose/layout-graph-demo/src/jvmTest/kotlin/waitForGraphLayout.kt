package net.akehurst.kotlin.compose.layout.graph.demo

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import net.akehurst.kotlin.compose.layout.graph.CompoundGraphLayoutViewTags

/**
 * Like waitForIdle, and also waits for every CompoundGraphLayoutView to finish its layouts
 * (they run off the UI thread, and a layout can lead to measurements and another layout).
 */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.waitForGraphLayout(timeoutMillis: Long = 60_000) {
    fun layingOut() = onAllNodesWithTag(CompoundGraphLayoutViewTags.LAYING_OUT, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    do {
        waitForIdle()
        if (!layingOut()) return
        waitUntil(timeoutMillis = timeoutMillis) { !layingOut() }
    } while (true)
}
