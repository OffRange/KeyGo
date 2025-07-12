package de.davis.keygo.item.viewing.presentation.password.model

import de.davis.keygo.core.domain.model.Score
import de.davis.keygo.totp.domain.model.TotpInformation


data class ViewPasswordState(
    val name: String = "",
    val password: ObfuscatedString = ObfuscatedString(""),
    val passwordStrengthScore: Score = Score.None,
    val totpInformation: TotpInformation = TotpInformation("", 0, 0),
    val username: String = "",
    val website: String = "",
    val note: String = "",
    val canOpenWebsite: Boolean = false,
    val modificationDialog: ModificationDialog? = null,
)