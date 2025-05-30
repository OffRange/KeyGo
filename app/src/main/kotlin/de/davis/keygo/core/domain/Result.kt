package de.davis.keygo.core.domain

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

sealed interface Result<S, E> {

    data class Failure<S, E>(val error: E) : Result<S, E>
    data class Success<S, E>(val success: S) : Result<S, E>
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

inline fun <S, E : F, F, MS> Result<S, E>.newResultOnSuccess(transform: (S) -> Result<MS, F>): Result<MS, F> {
    return when (this) {
        is Result.Success -> transform(success)
        is Result.Failure -> Result.Failure(error)
    }
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