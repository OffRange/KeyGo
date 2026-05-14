package de.davis.keygo.core.util

import kotlin.test.assertIs


fun <S, E> Result<S, E>.assertSuccess(): S =
    assertIs<Result.Success<S, E>>(this).success

fun <S, E> Result<S, E>.assertFailure(): E =
    assertIs<Result.Failure<S, E>>(this).error
