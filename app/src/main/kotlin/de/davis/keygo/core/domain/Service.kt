package de.davis.keygo.core.domain

sealed interface Service<out S> {

    data object Unavailable : Service<Nothing>
    data class Available<S>(val service: S) : Service<S>
}

fun <S> Service<S>.isAvailable(): Boolean = this is Service.Available

inline fun <S> Service<S>.executeIfAvailable(block: S.() -> Unit) = when (this) {
    Service.Unavailable -> Unit
    is Service.Available -> block(service)
}

fun <S> S?.asService(): Service<S> = this?.let { Service.Available(it) } ?: Service.Unavailable