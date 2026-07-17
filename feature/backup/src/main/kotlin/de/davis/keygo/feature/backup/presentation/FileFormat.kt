package de.davis.keygo.feature.backup.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.davis.keygo.feature.backup.R
import de.davis.keygo.feature.backup.domain.model.CsvPreset
import de.davis.keygo.feature.backup.domain.model.EncryptionMethod
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
        FileFormat.JSON -> Icons.Default.Lock
    }

internal val EncryptionMethod.displayName
    @Composable
    get() = stringResource(
        when (this) {
            EncryptionMethod.Passphrase -> R.string.encryption_method_passphrase
            EncryptionMethod.Ark -> R.string.encryption_method_ark
        }
    )

internal val EncryptionMethod.description
    @Composable
    get() = stringResource(
        when (this) {
            EncryptionMethod.Passphrase -> R.string.encryption_method_passphrase_description
            EncryptionMethod.Ark -> R.string.encryption_method_ark_description
        }
    )

internal val EncryptionMethod.icon
    get() = when (this) {
        EncryptionMethod.Passphrase -> Icons.Default.Password
        EncryptionMethod.Ark -> Icons.Default.PhoneAndroid
    }

internal val CsvPreset.displayName
    @Composable
    get() = stringResource(
        when (this) {
            CsvPreset.Browser -> R.string.csv_preset_browser
            CsvPreset.KeyGo -> R.string.csv_preset_keygo
        }
    )

internal val CsvPreset.description
    @Composable
    get() = stringResource(
        when (this) {
            CsvPreset.Browser -> R.string.csv_preset_browser_description
            CsvPreset.KeyGo -> R.string.csv_preset_keygo_description
        }
    )
