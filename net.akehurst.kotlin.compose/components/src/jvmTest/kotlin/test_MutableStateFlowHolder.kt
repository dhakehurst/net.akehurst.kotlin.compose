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

package net.akehurst.kotlin.compose.components.flowHolder

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class test_MutableStateFlowHolder {

    @Test
    fun value__initial() {
        val sut = mutableStateFlowHolderOf("initial")

        assertEquals("initial", sut.value)
    }


    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun value__onUpdate() = runTest {
        val sut = mutableStateFlowHolderOf("initial")

        var actual = Pair("unset","unset")
        val updatedJob = launch {
            sut.onUpdated(this, OnUpdatedStart.CURRENT_VALUES_FIRST) {
                actual = Pair(it.path.joinToString("."), it.value as String)
            }
        }
        advanceUntilIdle()
        assertEquals(Pair("","initial"), actual) // this coroutine needs to suspend before update occurs

        sut.update { "new value" }
        advanceUntilIdle()

        assertEquals(Pair("","new value"), actual)
        advanceUntilIdle()
        updatedJob.cancel()
    }
}