package de.davis.keygo.item.core.domain.model

import kotlinx.coroutines.Deferred

sealed interface FieldUpdate<out T> {
    data object Keep : FieldUpdate<Nothing>
    data object Clear : FieldUpdate<Nothing>
    data class Set<T>(val value: T) : FieldUpdate<T>
}

fun <T> FieldUpdate<T>.on(current: T?): T? = when (this) {
    FieldUpdate.Keep -> current
    FieldUpdate.Clear -> null
    is FieldUpdate.Set<T> -> value
}

suspend fun <T, A> FieldUpdate<T>.on(current: A?, deferred: Deferred<A>? = null): A? =
    when (this) {
        FieldUpdate.Keep -> current
        FieldUpdate.Clear -> null
        is FieldUpdate.Set<T> -> deferred?.await()
    }

fun <T> FieldUpdate<T>.withoutClearingOn(current: T): T = when (this) {
    FieldUpdate.Keep,
    FieldUpdate.Clear -> current

    is FieldUpdate.Set<T> -> value
}

fun <T, R> FieldUpdate<T>.onSet(block: (T) -> R): R? {
    if (this !is FieldUpdate.Set<T>)
        return null

    return block(value)
}

fun <T> FieldUpdate<T>.getValue(): T? {
    if (this !is FieldUpdate.Set<T>)
        return null

    return value
}

fun fieldUpdate(value: String?): FieldUpdate<String> = when {
    value == null -> keep()
    value.isBlank() -> clear()
    else -> set(value)
}

fun <T> keep(): FieldUpdate<T> = FieldUpdate.Keep
fun <T> clear(): FieldUpdate<T> = FieldUpdate.Clear
fun <T> set(value: T) = FieldUpdate.Set(value)