package de.davis.keygo.feature.backup.domain

import de.davis.keygo.core.security.domain.Session

/**
 * The ARK of the live session, or null when the app is locked.
 *
 * [Session.ark] throws rather than returning null, and every backup path has to treat "locked" as an
 * ordinary branch instead of an error, so the probe is written once here.
 */
internal fun Session.arkOrNull(): ByteArray? = runCatching { ark }.getOrNull()
