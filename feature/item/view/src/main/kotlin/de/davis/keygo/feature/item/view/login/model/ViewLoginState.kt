package de.davis.keygo.feature.item.view.login.model

import androidx.compose.runtime.Immutable
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.domain.model.Tag
import de.davis.keygo.core.item.domain.model.VaultMetadata

@Immutable
data class ViewLoginState(
    val name: String = "",
    val vaultMetadata: VaultMetadata? = null,
    val passkeyRPs: Set<String> = emptySet(),
    val password: ObfuscatedString? = null,
    val passwordStrengthScore: PasswordScore? = null,
    val totpState: TotpState = TotpState.NoTotp,
    val username: String = "",
    val domains: Set<DomainInfo> = emptySet(),
    val tags: Set<Tag> = emptySet(),
    val note: String = "",
    val modificationDialog: ModificationDialog? = null,
    val scanning: Boolean = false,
    val pinned: Boolean = false,
)
