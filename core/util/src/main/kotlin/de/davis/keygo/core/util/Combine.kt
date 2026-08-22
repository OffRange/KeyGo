package de.davis.keygo.core.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine as combineArray

fun <T1, T2, T3, T4, T5, T6, R> combine(
    flow1: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    transform: (T1, T2, T3, T4, T5, T6) -> R,
): Flow<R> = combineArray(flow1, flow2, flow3, flow4, flow5, flow6) { values ->
    @Suppress("UNCHECKED_CAST")
    transform(
        values[0] as T1,
        values[1] as T2,
        values[2] as T3,
        values[3] as T4,
        values[4] as T5,
        values[5] as T6,
    )
}

fun <T1, T2, T3, T4, T5, T6, T7, R> combine(
    flow1: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    flow7: Flow<T7>,
    transform: (T1, T2, T3, T4, T5, T6, T7) -> R,
): Flow<R> = combineArray(flow1, flow2, flow3, flow4, flow5, flow6, flow7) { values ->
    @Suppress("UNCHECKED_CAST")
    transform(
        values[0] as T1,
        values[1] as T2,
        values[2] as T3,
        values[3] as T4,
        values[4] as T5,
        values[5] as T6,
        values[6] as T7,
    )
}

fun <T1, T2, T3, T4, T5, T6, T7, T8, R> combine(
    flow1: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    flow7: Flow<T7>,
    flow8: Flow<T8>,
    transform: (T1, T2, T3, T4, T5, T6, T7, T8) -> R,
): Flow<R> = combineArray(flow1, flow2, flow3, flow4, flow5, flow6, flow7, flow8) { values ->
    @Suppress("UNCHECKED_CAST")
    transform(
        values[0] as T1,
        values[1] as T2,
        values[2] as T3,
        values[3] as T4,
        values[4] as T5,
        values[5] as T6,
        values[6] as T7,
        values[7] as T8,
    )
}
