package com.tomczyn.coroutines

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

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
    fun <R> Flow<R>.onEachToState(mapper: (T, R) -> T): Flow<R>
}

/**
 * Sealed interface representing the different launch strategies for merging flows.
 */
sealed interface Launched {
    data object Eagerly : Launched
    data class WhileSubscribed(val stopTimeoutMillis: Long = 0L) : Launched
    data object Lazily : Launched
}

@OptIn(ExperimentalCoroutinesApi::class)
private class MutableStateFlowWithStateInMerge<T>(
    private val scope: CoroutineScope,
    launched: Launched,
    private val state: MutableStateFlow<T>,
    private val flow: Array<out StateInMergeContext<T>.() -> Flow<*>>,
) : MutableStateFlow<T> by state {

    private val context: StateInMergeContext<T> = object : StateInMergeContext<T> {
        override val state: MutableStateFlow<T>
            get() = this@MutableStateFlowWithStateInMerge

        override fun <R> Flow<R>.onEachToState(mapper: (T, R) -> T): Flow<R> =
            onEach { value -> state.update { state -> mapper(state, value) } }
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
                val flowsList = produceFlows().toMutableList()
                val jobsList = launchMerge()
                state.subscriptionCount
                    .map { it > 0 }
                    .distinctUntilChanged()
                    .flatMapLatest { subscribed ->
                        flow<Unit> {
                            if (subscribed) {
                                launchInactive(jobsList, flowsList)
                            } else {
                                delay(launched.stopTimeoutMillis)
                                jobsList.forEach { job -> if (job.isActive) job.cancelAndJoin() }
                            }
                        }
                    }
                    .launchIn(this)
            }
        }
    }

    private fun produceFlows(): List<Flow<*>> = flow
        .map { produceFlow -> produceFlow(context) }

    private fun launchMerge(): Array<Job> = produceFlows()
        .map { flow -> flow.launchIn(scope) }
        .toTypedArray()

    private fun launchInactive(
        jobsList: Array<Job>,
        flowsList: MutableList<Flow<*>>
    ) {
        jobsList.forEachIndexed { index, job ->
            if (!job.isActive) jobsList[index] = flowsList[index].launchIn(scope)
        }
    }

    private suspend fun waitForFirstSubscriber() {
        state.subscriptionCount.first { it > 0 }
    }
}
