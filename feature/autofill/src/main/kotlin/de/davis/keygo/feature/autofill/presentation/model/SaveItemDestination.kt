package de.davis.keygo.feature.autofill.presentation.model

import androidx.navigation3.runtime.NavKey
import de.davis.keygo.feature.item.core.presentation.model.DetailPaneInformation
import kotlinx.serialization.Serializable

@Serializable
internal data class SaveItemDestination(
    val createRaw: DetailPaneInformation.CreateRaw
) : NavKey
