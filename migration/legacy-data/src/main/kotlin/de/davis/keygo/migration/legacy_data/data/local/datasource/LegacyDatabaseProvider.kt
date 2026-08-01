package de.davis.keygo.migration.legacy_data.data.local.datasource

internal interface LegacyDatabaseProvider {

    /**
     * Returns null when there is no file to open.
     *
     * An implementation must never bring the file into existence. This is a reader of an inherited
     * database, and a database it created itself would be indistinguishable from one it inherited.
     */
    fun get(): LegacyDatabase?

    /** Closes the open handle, if there is one. The next [get] opens a fresh one. */
    fun close()

    /** Closes and deletes the database and its `-wal`, `-shm` and `-journal` siblings. */
    fun delete(): Boolean
}
