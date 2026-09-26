package de.davis.keygo.feature.password_health.domain.model

sealed interface KeyGoNotification {
    data class NeedsAttention(val count: Int) : KeyGoNotification
}

val KeyGoNotification.isEmpty: Boolean
    get() = when (this) {
        is KeyGoNotification.NeedsAttention -> count <= 0
    }
