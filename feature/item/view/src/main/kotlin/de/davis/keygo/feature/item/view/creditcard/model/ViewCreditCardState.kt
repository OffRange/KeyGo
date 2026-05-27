package de.davis.keygo.feature.item.view.creditcard.model

import androidx.compose.runtime.Immutable
import de.davis.keygo.core.item.domain.model.Tag
import de.davis.keygo.core.item.domain.model.VaultMetadata
import de.davis.keygo.feature.item.view.login.model.ObfuscatedString

@Immutable
data class ViewCreditCardState(
    val name: String = "",
    val vaultMetadata: VaultMetadata? = null,
    val holder: String = "",
    val cardNumber: ObfuscatedString? = null,
    val lastNumbers: String = "",
    val cvv: ObfuscatedString? = null,
    val expirationDate: String = "",
    val tags: Set<Tag> = emptySet(),
    val note: String = "",
    val modificationDialog: ModificationDialog? = null,
    val pinned: Boolean = false,
)
