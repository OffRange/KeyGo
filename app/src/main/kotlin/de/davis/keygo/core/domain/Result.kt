package de.davis.keygo.core.domain

sealed interface Result<S, E> {

    data class Failure<S, E>(val error: E) : Result<S, E>
    data class Success<S, E>(val success: S) : Result<S, E>
}

fun <S, E> Result<S, E>.onSuccess(action: (S) -> Unit): Result<S, E> {
    if (this is Result.Success) {
        action(success)
    }
    return this
}

fun <S, E> Result<S, E>.onFailure(action: (E) -> Unit): Result<S, E> {
    if (this is Result.Failure) {
        action(error)
    }
    return this
}

fun <E> Boolean.asResult(fail: E): Result<Unit, E> = if (this) {
    Result.Success(Unit)
} else {
    Result.Failure(fail)
}