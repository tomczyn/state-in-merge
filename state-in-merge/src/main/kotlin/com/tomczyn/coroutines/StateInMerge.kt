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
 * @param flows A variable number of flows to merge.
 * @return A new [MutableStateFlow] containing the merged state.
 */
fun <T> MutableStateFlow<T>.stateInMerge(
    scope: CoroutineScope,
    launched: Launched,
    vararg flows: StateInMergeContext<T>.() -> Flow<*>,
): MutableStateFlow<T> = MutableStateFlowWithStateInMerge(
    scope = scope,
    state = this,
    launched = launched,
    lambdas = flows,
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
    lambdas: Array<out StateInMergeContext<T>.() -> Flow<*>>,
) : MutableStateFlow<T> by state {

    private val context: StateInMergeContext<T> = object : StateInMergeContext<T> {
        override val state: MutableStateFlow<T>
            get() = this@MutableStateFlowWithStateInMerge

        override fun <R> Flow<R>.onEachToState(mapper: (T, R) -> T): Flow<R> =
            onEach { value -> state.update { state -> mapper(state, value) } }
    }

    private val flows: List<Flow<*>> = lambdas
        .map { produceFlow -> produceFlow(context) }

    init {
        when (launched) {
            Launched.Eagerly -> launchAll()
            Launched.Lazily -> scope.launch {
                waitForFirstSubscriber()
                launchAll()
            }

            is Launched.WhileSubscribed -> {
                var jobs: Array<Job> = emptyArray()
                state.subscriptionCount
                    .map { it > 0 }
                    .distinctUntilChanged()
                    .flatMapLatest { subscribed ->
                        flow<Unit> {
                            when {
                                subscribed && jobs.isEmpty() -> jobs = launchAll()
                                subscribed -> launchCancelled(jobs)
                                !subscribed && jobs.isNotEmpty() -> {
                                    delay(launched.stopTimeoutMillis)
                                    jobs.cancelActive()
                                }
                            }
                        }
                    }
                    .launchIn(scope)
            }
        }
    }

    private suspend fun waitForFirstSubscriber() {
        state.subscriptionCount.first { it > 0 }
    }

    private fun launchAll(): Array<Job> = flows
        .map { flow -> flow.launchIn(scope) }
        .toTypedArray()

    private fun launchCancelled(jobs: Array<Job>) {
        check(jobs.size == flows.size)
        jobs.forEachIndexed { index, job ->
            if (job.isCancelled) jobs[index] = flows[index].launchIn(scope)
        }
    }

    private suspend fun Array<Job>.cancelActive() {
        forEach { job -> if (job.isActive) job.cancelAndJoin() }
    }
}
