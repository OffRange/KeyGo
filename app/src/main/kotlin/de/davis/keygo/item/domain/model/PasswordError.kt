package de.davis.keygo.item.domain.model

sealed interface PasswordError {
    data object BlankName : PasswordError
    data object BlankPassword : PasswordError
}