package de.davis.keygo.feature.password_health.domain.model

sealed interface KeyGoNotification {
    val kind: Kind

    data class NeedsAttention(val count: Int) : KeyGoNotification {
        override val kind = Kind.NeedsAttention
    }

    enum class Kind {
        NeedsAttention,
    }
}
