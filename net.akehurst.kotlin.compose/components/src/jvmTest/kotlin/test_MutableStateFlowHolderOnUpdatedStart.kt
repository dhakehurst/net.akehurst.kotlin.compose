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

@file:OptIn(ExperimentalCoroutinesApi::class)

package net.akehurst.kotlin.compose.components.flowHolder

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class test_MutableStateFlowHolderOnUpdatedStart {

    // Not backgroundScope: advanceUntilIdle does not run background work.
    private fun runWithScope(block: suspend TestScope.(CoroutineScope) -> Unit) = runTest {
        val scope = CoroutineScope(coroutineContext + Job())
        try {
            block(scope)
        } finally {
            scope.cancel()
        }
    }

    private fun List<CompositeEvent>.asStrings() = map { "${it.path.joinToString(".")}=${it.value}" }

    // --- single value ---

    @Test
    fun value__currentValuesFirst__reports_current_value() = runWithScope { scope ->
        val sut = mutableStateFlowHolderOf("a")
        val events = mutableListOf<CompositeEvent>()
        sut.onUpdated(scope, OnUpdatedStart.CURRENT_VALUES_FIRST) { events += it }
        advanceUntilIdle()

        assertEquals(listOf("=a"), events.asStrings())
    }

    @Test
    fun value__changesOnly__reports_only_changes() = runWithScope { scope ->
        val sut = mutableStateFlowHolderOf("a")
        val events = mutableListOf<CompositeEvent>()
        sut.onUpdated(scope) { events += it }
        advanceUntilIdle()
        assertEquals(emptyList(), events.asStrings())

        sut.update { "b" }
        advanceUntilIdle()
        assertEquals(listOf("=b"), events.asStrings())
    }

    @Test
    fun value__changesOnly__reports_change_made_before_collection_starts() = runWithScope { scope ->
        val sut = mutableStateFlowHolderOf("a")
        val events = mutableListOf<CompositeEvent>()
        sut.onUpdated(scope) { events += it }
        sut.update { "b" } // no advanceUntilIdle in between
        advanceUntilIdle()

        assertEquals(listOf("=b"), events.asStrings())
    }

    @Test
    fun value__changesOnly__nested_list() = runWithScope { scope ->
        val sut = mutableStateFlowHolderOf(mutableStateFlowHolderListOf(listOf("a", "b")))
        val events = mutableListOf<CompositeEvent>()
        sut.onUpdated(scope) { events += it }
        advanceUntilIdle()
        assertEquals(emptyList(), events.asStrings())

        sut.value.update(1) { "z" }
        advanceUntilIdle()
        assertEquals(listOf("1=z"), events.asStrings())
    }

    // --- list ---

    @Test
    fun list__currentValuesFirst__reports_each_element() = runWithScope { scope ->
        val sut = mutableStateFlowHolderListOf(listOf("a", "b", "c"))
        val events = mutableListOf<CompositeEvent>()
        sut.onUpdated(scope, OnUpdatedStart.CURRENT_VALUES_FIRST) { _, e -> events += e }
        advanceUntilIdle()

        assertEquals(listOf("0=a", "1=b", "2=c"), events.asStrings().sorted())
    }

    @Test
    fun list__changesOnly__reports_only_the_changed_element() = runWithScope { scope ->
        val sut = mutableStateFlowHolderListOf(listOf("a", "b", "c"))
        val events = mutableListOf<CompositeEvent>()
        var lastList = listOf<String>()
        sut.onUpdated(scope) { l, e -> events += e; lastList = l }
        advanceUntilIdle()
        assertEquals(emptyList(), events.asStrings())

        sut.update(1) { "z" }
        advanceUntilIdle()
        assertEquals(listOf("1=z"), events.asStrings())
        assertEquals(listOf("a", "z", "c"), lastList)
    }

    @Test
    fun list__changesOnly__reports_change_made_before_collection_starts() = runWithScope { scope ->
        val sut = mutableStateFlowHolderListOf(listOf("a", "b", "c"))
        val events = mutableListOf<CompositeEvent>()
        sut.onUpdated(scope) { _, e -> events += e }
        sut.update(2) { "z" } // no advanceUntilIdle in between
        advanceUntilIdle()

        assertEquals(listOf("2=z"), events.asStrings())
    }

    // --- map ---

    @Test
    fun map__currentValuesFirst__reports_each_entry() = runWithScope { scope ->
        val sut = mutableStateFlowHolderMapOf(mapOf("k1" to "a", "k2" to "b"))
        val events = mutableListOf<CompositeEvent>()
        sut.onUpdated(scope, OnUpdatedStart.CURRENT_VALUES_FIRST) { _, e -> events += e }
        advanceUntilIdle()

        assertEquals(listOf("k1=a", "k2=b"), events.asStrings().sorted())
    }

    @Test
    fun map__changesOnly__reports_only_the_changed_entry() = runWithScope { scope ->
        val sut = mutableStateFlowHolderMapOf(mapOf("k1" to "a", "k2" to "b"))
        val events = mutableListOf<CompositeEvent>()
        sut.onUpdated(scope) { _, e -> events += e }
        advanceUntilIdle()
        assertEquals(emptyList(), events.asStrings())

        sut.update("k2") { "z" }
        advanceUntilIdle()
        assertEquals(listOf("k2=z"), events.asStrings())
    }
}
