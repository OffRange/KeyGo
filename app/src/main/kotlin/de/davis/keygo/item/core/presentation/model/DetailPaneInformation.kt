package de.davis.keygo.item.core.presentation.model

import kotlinx.serialization.Serializable

sealed interface DetailPaneInformation {
    data class InitByDetailType(val detailType: DetailType) : DetailPaneInformation

    @Serializable
    sealed interface CreateRaw : DetailPaneInformation {
        val name: String

        @Serializable
        data class Password(
            override val name: String,
            val password: String,
            val username: String,
            val url: String,
        ) : CreateRaw
    }
}