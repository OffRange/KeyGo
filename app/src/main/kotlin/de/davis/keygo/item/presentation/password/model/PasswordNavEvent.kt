package de.davis.keygo.item.presentation.password.model

import kotlinx.collections.immutable.ImmutableList

sealed interface PasswordNavEvent {
    data object None : PasswordNavEvent
    data class ShowPasswordHints(val hints: ImmutableList<String>) : PasswordNavEvent
}