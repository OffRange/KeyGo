package de.davis.keygo.core.security.domain

fun interface SessionFactory {
    fun create(): Session
}
