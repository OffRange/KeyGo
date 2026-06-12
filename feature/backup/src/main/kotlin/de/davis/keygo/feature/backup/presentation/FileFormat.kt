package de.davis.keygo.feature.backup.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.davis.keygo.feature.backup.R
import de.davis.keygo.feature.backup.domain.model.FileFormat

internal val FileFormat.displayName
    @Composable
    get() = stringResource(
        R.string.file_type_backup,
        name
    )

internal val FileFormat.icon
    get() = when (this) {
        FileFormat.CSV -> Icons.AutoMirrored.Default.List
        FileFormat.KDBX -> Icons.Default.Lock
    }
