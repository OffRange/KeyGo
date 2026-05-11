package de.davis.keygo.feature.item.view.login.model

import androidx.compose.runtime.Immutable
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.domain.model.VaultMetadata
import de.davis.keygo.feature.totp.domain.model.TotpValue

@Immutable
data class ViewLoginState(
    val name: String = "",
    val vaultMetadata: VaultMetadata? = null,
    val passkeyRPs: Set<String> = emptySet(),
    val password: ObfuscatedString = ObfuscatedString(""),
    val passwordStrengthScore: PasswordScore = PasswordScore.None,
    val totpValue: TotpValue = TotpValue("", 0, 0),
    val username: String = "",
    val domains: Set<DomainInfo> = emptySet(),
    val note: String = "",
    val modificationDialog: ModificationDialog? = null,
    val scanning: Boolean = false,
    val pinned: Boolean = false,
)
