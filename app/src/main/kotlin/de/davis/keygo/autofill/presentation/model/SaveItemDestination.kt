package de.davis.keygo.autofill.presentation.model

import de.davis.keygo.item.core.presentation.model.DetailPaneInformation
import kotlinx.serialization.Serializable

@Serializable
data class SaveItemDestination(
    val createRaw: DetailPaneInformation.CreateRaw
)