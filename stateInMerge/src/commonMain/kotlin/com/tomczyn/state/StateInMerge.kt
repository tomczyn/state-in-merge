package com.tomczyn.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Merges the given flows into a new [MutableStateFlow] with the provided initial state,
 * scope, and launch strategy.
 *
 * @param T The type of the state held by the [MutableStateFlow].
 * @param scope The [CoroutineScope] in which the merging will occur.
 * @param launched The launch strategy to use for merging flows.
 * @param flow A variable number of flows to merge.
 * @return A new [MutableStateFlow] containing the merged state.
 */
@ExperimentalCoroutinesApi
fun <T> MutableStateFlow<T>.stateInMerge(
    scope: CoroutineScope,
    launched: Launched,
    vararg flow: StateInMergeContext<T>.() -> Flow<*>,
): MutableStateFlow<T> = MutableStateFlowWithStateInMerge(
    scope = scope,
    state = this,
    launched = launched,
    flow = flow,
)

/**
 * Interface representing the context for merging states in [MutableStateFlow].
 *
 * @param T The type of the state held by the [MutableStateFlow].
 */
interface StateInMergeContext<T> {
    val state: MutableStateFlow<T>
    fun <R> Flow<R>.onEachToState(mapper: (R, T) -> T): Flow<R>
}

/**
 * Sealed interface representing the different launch strategies for merging flows.
 */
sealed interface Launched {
    object Eagerly : Launched
    data class WhileSubscribed(val stopTimeoutMillis: Long = 0L) : Launched
    object Lazily : Launched
}

@ExperimentalCoroutinesApi
private class MutableStateFlowWithStateInMerge<T>(
    private val scope: CoroutineScope,
    launched: Launched,
    private val state: MutableStateFlow<T>,
    private val flow: Array<out StateInMergeContext<T>.() -> Flow<*>>,
) : MutableStateFlow<T> by state {

    private val context: StateInMergeContext<T> = object : StateInMergeContext<T> {
        override val state: MutableStateFlow<T>
            get() = this@MutableStateFlowWithStateInMerge

        override fun <R> Flow<R>.onEachToState(mapper: (R, T) -> T): Flow<R> =
            onEach { value -> state.update { state -> mapper(value, state) } }
    }

    init {
        when (launched) {
            Launched.Eagerly -> launchMerge()
            Launched.Lazily -> scope.launch {
                waitForFirstSubscriber()
                flow.map { produceFlow -> produceFlow(context) }
                    .merge()
                    .collect()
            }

            is Launched.WhileSubscribed -> scope.launch {
                waitForFirstSubscriber()
                var mergeJob = launchMerge()
                state.subscriptionCount
                    .map { it > 0 }
                    .distinctUntilChanged()
                    .flatMapLatest { subscribed ->
                        if (subscribed) {
                            if (!mergeJob.isActive) mergeJob = launchMerge()
                        } else {
                            delay(launched.stopTimeoutMillis)
                            mergeJob.cancelAndJoin()
                        }
                        flowOf(Unit)
                    }
                    .launchIn(this)
            }
        }
    }

    private fun launchMerge(): Job = flow.map { produceFlow -> produceFlow(context) }.merge()
        .launchIn(scope)

    private suspend fun waitForFirstSubscriber() {
        state.subscriptionCount.first { it > 0 }
    }
}
