package de.davis.keygo.item.viewing.presentation.password.model

import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.model.Password
import de.davis.keygo.totp.domain.model.TotpInformation


data class ViewPasswordState(
    val name: String = "",
    val password: ObfuscatedString = ObfuscatedString(""),
    val passwordStrengthScore: Password.Score = Password.Score.None,
    val totpInformation: TotpInformation = TotpInformation("", 0, 0),
    val username: String = "",
    val domains: Set<DomainInfo> = emptySet(),
    val note: String = "",
    val modificationDialog: ModificationDialog? = null,
)