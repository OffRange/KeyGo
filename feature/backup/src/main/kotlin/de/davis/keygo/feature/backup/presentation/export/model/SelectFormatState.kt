package de.davis.keygo.feature.backup.presentation.export.model

import androidx.compose.runtime.Stable
import de.davis.keygo.feature.backup.domain.model.FileFormat

@Stable
internal data class SelectFormatState(
    val format: FileFormat? = null
)