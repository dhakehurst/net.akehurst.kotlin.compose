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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class test_MutableStateFlowHolderUpdates {

    @Test
    fun list_updateAll_shrinks() {
        val sut = mutableStateFlowHolderListOf(listOf("a", "b", "c"))
        sut.updateAll { listOf("x") }
        assertEquals(listOf("x"), sut.value)
    }

    @Test
    fun list_updateAll_grows() {
        val sut = mutableStateFlowHolderListOf(listOf("a"))
        sut.updateAll { listOf("x", "y", "z") }
        assertEquals(listOf("x", "y", "z"), sut.value)
    }

    @Test
    fun list_removeAt() {
        val sut = mutableStateFlowHolderListOf(listOf("a", "b", "c"))
        sut.removeAt(1)
        assertEquals(listOf("a", "c"), sut.value)
    }

    @Test
    fun map_updateAll_removesMissingKeys() {
        val sut = mutableStateFlowHolderMapOf(mapOf("a" to 1, "b" to 2))
        sut.updateAll { mapOf("b" to 20, "c" to 3) }
        assertEquals(mapOf("b" to 20, "c" to 3), sut.value)
    }

    @Test
    fun map_remove() {
        val sut = mutableStateFlowHolderMapOf(mapOf("a" to 1, "b" to 2))
        sut.remove("a")
        assertEquals(mapOf("b" to 2), sut.value)
    }

    @Test
    fun contentFlow_followsReplacedNestedHolder_moreThan16Times() = runTest {
        val sut = mutableStateFlowHolderOf(mutableStateFlowHolderOf(0))
        val events = mutableListOf<CompositeEvent>()
        val job = launch { sut.contentFlow.collect { events.add(it) } }
        advanceUntilIdle()
        repeat(20) { i ->
            sut.update { mutableStateFlowHolderOf(100 + i) }
            advanceUntilIdle()
        }
        sut.value.update { 999 }
        advanceUntilIdle()
        job.cancel()
        assertEquals(999, events.last().value, "events=${events.map { it.value }}")
    }

    @Test
    fun contentFlow_ignoresReplacedNestedHolder() = runTest {
        val old = mutableStateFlowHolderOf(0)
        val sut = mutableStateFlowHolderOf(old)
        val events = mutableListOf<CompositeEvent>()
        val job = launch { sut.contentFlow.collect { events.add(it) } }
        advanceUntilIdle()
        sut.update { mutableStateFlowHolderOf(1) }
        advanceUntilIdle()
        events.clear()
        old.update { 42 }
        advanceUntilIdle()
        job.cancel()
        assertTrue(events.none { it.value == 42 }, "events=${events.map { it.value }}")
    }
}
