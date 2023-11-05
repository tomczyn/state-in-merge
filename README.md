# state-in-merge

`state-in-merge` is a Kotlin library that provides an extension function for merging multiple flows into a single `MutableStateFlow`. It's similiar to `stateIn` extension but can be applied to existing `MutableStateFlow` object with capability to merge multiple flows into single `StateFlow`. It simplifies the process of updating a shared state based on multiple input flows by offering different launch strategies to control the flow execution.

## Description

`state-in-merge` enables you to merge the states of multiple flows into a single `MutableStateFlow` using different launch strategies, such as `Eagerly`, `Lazily`, and `WhileSubscribed`. This library is particularly useful when working with complex state management scenarios where you need to combine multiple streams of data into a single state flow.

You can read more about the extension in this blog post: https://easycontext.io/the-problem-with-statein-operator/

## Adding as a Dependency

To add `state-in-merge` as a dependency in your project, add the following to your `build.gradle` file:

```groovy
dependencies {
    implementation 'com.tomczyn.coroutines:state-in-merge:1.1.0'
}
```

For `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.tomczyn.coroutines:state-in-merge:1.1.0")
}
```

## Example Usage

Here're a few examples of how to use the `stateInMerge` extension function:

```kotlin
class MyViewModel(
    repo: MyRepo
) : ViewModel() {

    val state: StateFlow<MyState> 
        get() = _state
    private val _state: MutableStateFlow<MyState> = MutableStateFlow(MyState())
        .stateInMerge(
            scope = viewModelScope,
            launched = Launched.WhileSubscribed(stopTimeoutMillis = 5_000),
            { repo.data1().onEachToState { state, data -> state.copy(data1 = data) } }, 
            { repo.data2().onEachToState { state, data -> state.copy(data2 = data) } }, 
        )
    ...
}
```


```kotlin
data class AppState(val countA: Int, val countB: Int)

fun main() = runBlocking {
    val state = MutableStateFlow(AppState(0, 0))

    // Define the flows to merge
    val flowA = flow { emitAll((1..5).asFlow().map { it * 2 }) }
    val flowB = flow { emitAll((1..5).asFlow().map { it * 3 }) }

    val mergedState = state.stateInMerge(
        scope = this,
        launched = Launched.Eagerly,
        { flowA.onEachToState { value, state -> state.copy(countA = value) } },
        { flowB.onEachToState { value, state -> state.copy(countB = value) } },
    )

    // Collect the merged state
    mergedState.collect {
        println(it)
    }
}
```

## License

`state-in-merge` is available under the MIT license. See the [LICENSE](LICENSE) file for more information.

```
MIT License

Copyright (c) 2023 Maciej Tomczynski

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
