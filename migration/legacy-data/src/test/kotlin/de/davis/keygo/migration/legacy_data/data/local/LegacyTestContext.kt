package de.davis.keygo.migration.legacy_data.data.local

import android.content.Context
import android.content.ContextWrapper
import java.io.File

/**
 * A [Context] that answers `getDatabasePath` with [databaseFile] and nothing else.
 *
 * Both production classes in this module that take a Context use it only to turn
 * `secure_element_database` into a path, so pointing that one call at a temp file is the whole of
 * what a test needs. A relaxed mock would do it too, but it would answer *every* call, which is the
 * opposite of what is wanted here: an unstubbed answer is how a test ends up quietly reading some
 * other file and reporting an empty database. Everything not overridden below throws instead.
 *
 * The two extra overrides are not guesses, they are what Room's android builder actually reaches
 * for on the way to opening a file, and both are answered as narrowly as possible:
 * - `getSystemService` returns null. Room asks for `ActivityManager` to decide whether it is on a
 *   low RAM device, and treats an absent service the same as a device that is not.
 * - `getApplicationContext` returns this, because a null base context has none to delegate to.
 *
 * A `ContextWrapper` with a null base rather than a subclass of `Context`, because `Context` is
 * abstract across dozens of members and none of the others are ever called.
 */
internal fun legacyContext(databaseFile: File): Context = object : ContextWrapper(null) {

    override fun getDatabasePath(name: String?): File = databaseFile

    override fun getSystemService(name: String): Any? = null

    override fun getApplicationContext(): Context = this
}
