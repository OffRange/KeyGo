package de.davis.keygo.feature.password_health.presentation.model

import androidx.compose.runtime.Stable
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.feature.password_health.domain.model.PasswordFixError

@Stable
internal sealed interface FixFlow {
    val itemId: ItemId

    data class Generating(override val itemId: ItemId) : FixFlow

    data class Pending(
        override val itemId: ItemId,
        val password: String,
        val applying: Boolean = false,
        val error: PasswordFixError? = null,
    ) : FixFlow
}
