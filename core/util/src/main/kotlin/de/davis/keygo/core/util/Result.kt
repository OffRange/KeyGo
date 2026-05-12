package de.davis.keygo.core.util

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

sealed interface Result<out S, out E> {

    data class Failure<out S, out E>(val error: E) : Result<S, E>
    data class Success<out S, out E>(val success: S) : Result<S, E>
}


@OptIn(ExperimentalContracts::class)
fun <S, E> Result<S, E>.isSuccess(): Boolean {
    contract {
        returns(true) implies (this@isSuccess is Result.Success)
    }
    return this is Result.Success
}

@OptIn(ExperimentalContracts::class)
fun <S, E> Result<S, E>.isFailure(): Boolean {
    contract {
        returns(true) implies (this@isFailure is Result.Failure)
    }
    return this is Result.Failure
}

inline fun <S, E> Result<S, E>.onSuccess(action: (S) -> Unit): Result<S, E> {
    if (this is Result.Success) {
        action(success)
    }
    return this
}

inline fun <S, E> Result<S, E>.onFailure(action: (E) -> Unit): Result<S, E> {
    if (this is Result.Failure) {
        action(error)
    }
    return this
}

inline fun <S, E, MS> Result<S, E>.mapSuccess(transform: (S) -> MS): Result<MS, E> {
    return when (this) {
        is Result.Success -> Result.Success(transform(success))
        is Result.Failure -> Result.Failure(error)
    }
}

inline fun <S, E, ME> Result<S, E>.mapFailure(transform: (E) -> ME): Result<S, ME> {
    return when (this) {
        is Result.Success -> Result.Success(success)
        is Result.Failure -> Result.Failure(transform(error))
    }
}

fun <S, E> Result<S, E>.asUnitResult(): Result<Unit, E> = mapSuccess {}

fun <S, E> Result<S, E>.getOrNull(): S? = when (this) {
    is Result.Success -> success
    is Result.Failure -> null
}


fun <E> Boolean.asResult(fail: E): Result<Unit, E> = if (this) {
    Result.Success(Unit)
} else {
    Result.Failure(fail)
}

fun <S, E> S?.asResult(onNullError: E): Result<S, E> =
    if (this == null) Result.Failure(onNullError)
    else Result.Success(this)


inline fun <S, E, R> Result<S, E>.fold(onSuccess: (S) -> R, onFailure: (E) -> R): R = when (this) {
    is Result.Success -> onSuccess(success)
    is Result.Failure -> onFailure(error)
}

class ResultBinding<E> {
    class Abort(val error: Any?) : Throwable()

    fun <S> Result<S, E>.bind(): S {
        return when (this) {
            is Result.Success -> success
            is Result.Failure -> throw Abort(error)
        }
    }

    fun <S, F> Result<S, F>.bind(mapError: (F) -> E): S {
        return when (this) {
            is Result.Success -> success
            is Result.Failure -> throw Abort(mapError(error))
        }
    }
}

inline fun <S, E> resultBinding(
    block: ResultBinding<E>.() -> S
): Result<S, E> {
    val binding = ResultBinding<E>()

    return try {
        Result.Success(binding.block())
    } catch (e: ResultBinding.Abort) {
        @Suppress("UNCHECKED_CAST")
        Result.Failure(e.error as E)
    }
}
