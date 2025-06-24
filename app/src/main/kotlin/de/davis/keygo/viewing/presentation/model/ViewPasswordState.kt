package de.davis.keygo.viewing.presentation.model

import de.davis.keygo.core.domain.model.Score


data class ViewPasswordState(
    val name: String = "",
    val password: ObfuscatedString = ObfuscatedString(""),
    val passwordStrengthScore: Score = Score.None,
    val username: String = "",
    val website: String = "",
    val note: String = "",
    val canOpenWebsite: Boolean = false,
    val modificationDialog: ModificationDialog? = null,
)