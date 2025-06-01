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

data class SuccessPair2<S1, S2>(val success1: S1, val success2: S2)
data class SuccessPair3<S1, S2, S3>(val success1: S1, val success2: S2, val success3: S3)
data class SuccessPair4<S1, S2, S3, S4>(
    val success1: S1,
    val success2: S2,
    val success3: S3,
    val success4: S4
)

inline fun <S, S2, E : F, F> Result<S, E>.zip(transform: (S) -> Result<S2, F>): Result<SuccessPair2<S, S2>, F> =
    when (this) {
        is Result.Success -> transform(success).mapSuccess { SuccessPair2(success, it) }
        is Result.Failure -> Result.Failure(error)
    }

inline fun <S1, S2, S3, E : F, F> Result<SuccessPair2<S1, S2>, E>.zip(transform: (S1, S2) -> Result<S3, F>): Result<SuccessPair3<S1, S2, S3>, F> =
    when (this) {
        is Result.Success -> with(success) {
            transform(success1, success2).mapSuccess { SuccessPair3(success1, success2, it) }
        }

        is Result.Failure -> Result.Failure(error)
    }

inline fun <S1, S2, S3, S4, E : F, F> Result<SuccessPair3<S1, S2, S3>, E>.zip(transform: (S1, S2, S3) -> Result<S4, F>): Result<SuccessPair4<S1, S2, S3, S4>, F> =
    when (this) {
        is Result.Success -> with(success) {
            transform(success1, success2, success3).mapSuccess {
                SuccessPair4(
                    success1,
                    success2,
                    success3,
                    it
                )
            }
        }

        is Result.Failure -> Result.Failure(error)
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