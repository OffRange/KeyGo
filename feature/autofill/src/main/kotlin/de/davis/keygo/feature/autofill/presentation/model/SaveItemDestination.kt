package de.davis.keygo.feature.autofill.presentation.model

import de.davis.keygo.feature.item.core.presentation.model.DetailPaneInformation
import kotlinx.serialization.Serializable

@Serializable
internal data class SaveItemDestination(
    val createRaw: DetailPaneInformation.CreateRaw
)