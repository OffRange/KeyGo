package de.davis.keygo.core.item.domain.model

data class PasswordCredential(
    val secret: PasswordSecret,
    val score: PasswordScore,
)
