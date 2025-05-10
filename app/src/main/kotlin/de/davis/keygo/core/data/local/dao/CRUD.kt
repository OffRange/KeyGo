package de.davis.keygo.core.data.local.dao

interface CRUD<in I> {

    suspend fun insert(item: I): Long
}