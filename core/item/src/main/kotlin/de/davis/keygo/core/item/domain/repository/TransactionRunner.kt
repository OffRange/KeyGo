package de.davis.keygo.core.item.domain.repository

interface TransactionRunner {
    suspend fun <R> runInTransaction(block: suspend () -> R): R
}