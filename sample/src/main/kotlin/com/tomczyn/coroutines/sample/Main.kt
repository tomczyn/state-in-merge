package com.tomczyn.coroutines.sample

import com.tomczyn.coroutines.Launched
import com.tomczyn.coroutines.stateInMerge
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlin.system.exitProcess


suspend fun main(args: Array<String>): Unit = coroutineScope {
    val viewModel = ViewModel(viewModelScope = this)
    val job = launch {
        viewModel.state.collect { state ->
            println("State received: $state")
        }
    }
    delay(2000)
    job.cancelAndJoin()
    println("Collection cancelled for some time")
    delay(4000) // Wait for Launched.WhileSubscribed timeout
    println("Resuming collection...")
    launch {
        viewModel.state.collect { state ->
            println("State after resuming: $state")
        }
    }
    delay(5000)
    exitProcess(0)
}

data class MyState(val data1: Int = 0, val data2: Int = 0)

class ViewModel(
    viewModelScope: CoroutineScope,
    private val repo: Repository = Repository()
) {

    val state: StateFlow<MyState>
        get() = _state
    private val _state: MutableStateFlow<MyState> = MutableStateFlow(MyState())
        .stateInMerge(
            scope = viewModelScope,
            launched = Launched.WhileSubscribed(stopTimeoutMillis = 5_000),
            { repo.fetchData1().onEachToState { state, data -> state.copy(data1 = data) } },
            { repo.fetchData2().onEachToState { state, data -> state.copy(data2 = data) } },
        )
}

class Repository {
    fun fetchData1(): Flow<Int> = flow {
        var i = 0
        repeat(5) { emit(i++); delay(500) }
    }

    fun fetchData2(): Flow<Int> = flow {
        var i = 0
        repeat(20) { emit(i++); delay(500) }
    }
}
