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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.collections.plus
import kotlin.coroutines.EmptyCoroutineContext

interface MutableStateFlowHolderComposite {
    /**
     * Flow of Pair( path, value )
     */
    val contentFlow: Flow<CompositeEvent>
}

data class CompositeEvent(
    val path: List<Any>,
    val value: Any? = null,
)

fun <T : Any?> mutableStateFlowHolderOf(initialValue: T) = MutableStateFlowHolder(initialValue)

class MutableStateFlowHolder<T : Any?>(initialValue: T): MutableStateFlowHolderComposite {
    private val _mutableStateFlow = MutableStateFlow(initialValue)
    private var _listeners: MutableList<(T) -> Unit>? = null

    val stateFlow = _mutableStateFlow.asStateFlow()
    val value get() = _mutableStateFlow.value
    override val contentFlow get() = stateFlow.flatMapLatest { fel ->
        when (fel) {
            is MutableStateFlowHolderComposite -> fel.contentFlow.map { (ck, cv) ->
                CompositeEvent( ck, cv)
            }

            else -> flowOf(
                CompositeEvent(listOf(), fel)
            )
        }
    }

    fun update(provider: (T) -> T) {
        _mutableStateFlow.update {
            provider.invoke(it)
        }
    }

    @Composable
    fun collectAsState() = stateFlow.collectAsState()

    fun onUpdated(scope: CoroutineScope, action: suspend (CompositeEvent) -> Unit) {
        scope.launch {
            contentFlow
                .shareIn(scope, SharingStarted.Eagerly, 0)
                .collect {
                    action.invoke(it)
                }
        }
    }
}

fun <E : Any?> mutableStateFlowHolderListOf(initialValue: List<E>) = MutableStateFlowHolderList<E>(initialValue)
class MutableStateFlowHolderList<E : Any?>(initialValue: List<E>) : MutableStateFlowHolderComposite {

    private val _listFlow = mutableStateFlowHolderOf(initialValue.map {
        mutableStateFlowHolderOf(it)
    })

    val value get() = _listFlow.value.map { v -> v.value }
    val listStateFlow = _listFlow.stateFlow
    override val contentFlow: Flow<CompositeEvent>
        get() = listStateFlow
            .flatMapLatest { list ->
                if (list.isEmpty()) {
                    emptyFlow()
                } else {
                    list.mapIndexed { idx, el ->
                        el.stateFlow.flatMapLatest { fel ->
                            when (fel) {
                                is MutableStateFlowHolderComposite -> fel.contentFlow.map { (ck, cv) ->
                                    CompositeEvent(listOf(idx) + ck, cv)
                                }

                                else -> flowOf(
                                    CompositeEvent(listOf(idx), fel)
                                )
                            }
                        }
                    }.merge()
                }
            }

    fun update(index: Int, provider: (E?) -> E) {
        val size = _listFlow.value.size
        when {
            index < 0 -> error("Invalid index (< 0) for MutableStateFlowHolderList $index, size is ${size}")
            index < size -> {
                val mutableFlow = _listFlow.value[index]
                mutableFlow.update { v -> provider.invoke(v) }
            }

            index == size -> {
                val mutableFlow = mutableStateFlowHolderOf(provider.invoke(null))
                _listFlow.update { v -> v + mutableFlow }
            }

            else -> error("Invalid index (> size) for MutableStateFlowHolderList $index, size is ${size}")
        }
    }

    /**
     * Replace the whole content with the result of [provider].
     * Existing elements are updated in place, extra elements are appended,
     * and elements beyond the new size are removed.
     */
    fun updateAll(provider: (List<E>) -> List<E>) {
        val newValues = provider.invoke(value)
        newValues.forEachIndexed { idx, v ->
            update(idx) { v }
        }
        if (newValues.size < _listFlow.value.size) {
            _listFlow.update { v -> v.subList(0, newValues.size).toList() }
        }
    }

    fun removeAt(index: Int) {
        val size = _listFlow.value.size
        if (index < 0 || index >= size) error("Invalid index for MutableStateFlowHolderList $index, size is ${size}")
        _listFlow.update { v -> v.filterIndexed { i, _ -> i != index } }
    }

    @Composable
    fun collectAsState() = listStateFlow.collectAsState()

    fun onUpdated(scope: CoroutineScope, action: suspend (List<E>, CompositeEvent) -> Unit) {
        scope.launch(EmptyCoroutineContext + CoroutineName("Element UpdateNotifier for MutableStateFlowHolderList ${this.hashCode()}")) {
            contentFlow
                .shareIn(this, SharingStarted.Eagerly, 0) // use values from now onwards
                .collect { e ->
                    action.invoke(value,  e)
                }
        }
    }

}

fun <K : Any, V> mutableStateFlowHolderMapOf(initialValue: Map<K, V>) = MutableStateFlowHolderMap<K, V>(initialValue)
class MutableStateFlowHolderMap<K : Any, V>(initialValue: Map<K, V>) : MutableStateFlowHolderComposite {

    private val _mapStateFlow = mutableStateFlowHolderOf(initialValue.entries.associate { (k, v) ->
        Pair(k, mutableStateFlowHolderOf(v))
    })

    val value get() = _mapStateFlow.value.entries.associate { (k, v) -> Pair(k, v.value) }
    val mapStateFlow = _mapStateFlow.stateFlow
    override val contentFlow
        get() = mapStateFlow
            .flatMapLatest { map ->
                if (map.isEmpty()) {
                    emptyFlow()
                } else {
                    map.map { (k, v) ->
                        v.stateFlow.flatMapLatest { el ->
                            when {
                                el is MutableStateFlowHolderComposite -> el.contentFlow.map { (ck, cv) -> CompositeEvent(listOf(k) + ck, cv) }
                                else -> flowOf(CompositeEvent(listOf(k), el))
                            }
                        }
                    }.merge()
                }
            }

    operator fun get(key: K) = _mapStateFlow.value[key]

    fun update(k: K, provider: (V?) -> V) {
        var mutableFlow = _mapStateFlow.value[k]
        if (null == mutableFlow) {
            mutableFlow = mutableStateFlowHolderOf(provider.invoke(null))
            _mapStateFlow.update { v -> v + Pair(k, mutableFlow) }
        } else {
            mutableFlow.update { v -> provider.invoke(v) }
        }
    }

    /**
     * Replace the whole content with the result of [provider].
     * Existing entries are updated in place, new keys are added,
     * and keys not in the result are removed.
     */
    fun updateAll(provider: (Map<K, V>) -> Map<K, V>) {
        val newMap = provider.invoke(value)
        newMap.entries.forEach { (k, v) ->
            update(k) { v }
        }
        val removedKeys = _mapStateFlow.value.keys - newMap.keys
        if (removedKeys.isNotEmpty()) {
            _mapStateFlow.update { v -> v - removedKeys }
        }
    }

    fun remove(key: K) {
        if (_mapStateFlow.value.containsKey(key)) {
            _mapStateFlow.update { v -> v - key }
        }
    }

    @Composable
    fun collectAsState() = mapStateFlow.collectAsState()

    fun onUpdated(scope: CoroutineScope, action: suspend (Map<K, V>, CompositeEvent) -> Unit) {
        scope.launch(EmptyCoroutineContext + CoroutineName("Element UpdateNotifier for MutableStateFlowHolderMap ${this.hashCode()}")) {
            contentFlow
                .shareIn(this, SharingStarted.Eagerly, 0) // use values from now onwards
                .collect { e ->
                    action.invoke(value, e)
                }
        }
    }

}

fun mergeParts(vararg parts: Pair<Any, Flow<Any?>>): Flow<CompositeEvent> = parts.map { (k, f) ->
    f.map { v ->
        when (v) {
            is CompositeEvent -> CompositeEvent(listOf(k) + v.path, v.value)
            else -> CompositeEvent(listOf(k), v)
        }
    }
}.merge()