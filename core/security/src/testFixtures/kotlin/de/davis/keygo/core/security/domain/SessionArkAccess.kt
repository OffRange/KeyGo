package de.davis.keygo.core.security.domain

import de.davis.keygo.core.util.Result

fun Session.exportArk(): Result<ByteArray, SessionError> = catching { binding.exportArk() }
