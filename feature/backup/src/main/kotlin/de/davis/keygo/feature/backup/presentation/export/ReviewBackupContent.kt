package de.davis.keygo.feature.backup.presentation.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.presentation.StrengthIndicator
import de.davis.keygo.feature.backup.R
import de.davis.keygo.feature.backup.domain.model.BackupDestination
import de.davis.keygo.feature.backup.domain.model.BackupInterval
import de.davis.keygo.feature.backup.domain.model.CsvPreset
import de.davis.keygo.feature.backup.domain.model.EncryptionMethod
import de.davis.keygo.feature.backup.domain.model.FileFormat
import de.davis.keygo.feature.backup.domain.model.IntervalUnit
import de.davis.keygo.feature.backup.presentation.displayName
import de.davis.keygo.feature.backup.presentation.component.IconBadge
import de.davis.keygo.feature.backup.presentation.export.model.ProvidePassphraseState
import de.davis.keygo.feature.backup.presentation.export.model.ScheduleMode
import de.davis.keygo.feature.backup.presentation.export.model.SelectDestinationState
import de.davis.keygo.feature.backup.presentation.export.model.SelectScheduleState
import de.davis.keygo.feature.backup.presentation.icon
import de.davis.keygo.feature.backup.displayName as intervalDisplayName

@Composable
internal fun ReviewBackupContent(
    format: FileFormat,
    scheduleState: SelectScheduleState,
    destinationState: SelectDestinationState,
    passphraseState: ProvidePassphraseState,
    csvPreset: CsvPreset,
) {
    val encrypted = format.encrypted
    val recurring = scheduleState.mode == ScheduleMode.Recurring
    Surface {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ReviewHeroCard(format = format)

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    ReviewRow(
                        icon = format.icon,
                        label = stringResource(R.string.review_section_format),
                    ) {
                        Text(text = format.displayName)
                    }

                    ReviewDivider()

                    ReviewRow(
                        icon = scheduleState.mode.icon,
                        label = stringResource(R.string.review_section_schedule),
                    ) {
                        Text(
                            text = if (recurring) scheduleState.interval.intervalDisplayName
                            else stringResource(R.string.review_schedule_one_time),
                        )
                    }

                    if (recurring) {
                        ReviewDivider()
                        ReviewRow(
                            icon = Icons.Default.DeleteSweep,
                            label = stringResource(R.string.review_section_retention),
                        ) {
                            Text(
                                text = if (scheduleState.keepAll) stringResource(R.string.review_retention_all)
                                else pluralStringResource(
                                    R.plurals.review_retention,
                                    scheduleState.keepCount,
                                    scheduleState.keepCount,
                                ),
                            )
                        }
                    }

                    ReviewDivider()

                    ReviewRow(
                        icon = Icons.Default.Folder,
                        label = stringResource(R.string.review_section_destination),
                    ) {
                        destinationState.destination?.let { destination ->
                            Text(text = destination.displayPath)
                        }
                    }

                    ReviewDivider()

                    ReviewRow(
                        icon = Icons.Default.Inventory2,
                        label = stringResource(R.string.review_section_contents),
                    ) {
                        Text(
                            text = stringResource(
                                if (encrypted) R.string.review_contents_all
                                else R.string.review_contents_logins,
                            ),
                        )
                    }

                    ReviewDivider()

                    ReviewRow(
                        icon = if (encrypted) Icons.Default.Shield else Icons.Default.LockOpen,
                        label = stringResource(R.string.review_section_encryption),
                        iconTint = if (encrypted) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                    ) {
                        Text(
                            text = stringResource(
                                if (encrypted) R.string.review_encryption_on
                                else R.string.review_encryption_off,
                            ),
                            color = if (encrypted) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                        )
                    }

                    if (encrypted) {
                        ReviewDivider()
                        if (passphraseState.method == EncryptionMethod.Passphrase)
                            ReviewRow(
                                icon = Icons.Default.Password,
                                label = stringResource(R.string.review_section_passphrase),
                            ) {
                                StrengthIndicator(passwordScore = passphraseState.passphraseScore)
                            }
                        else
                            ReviewRow(
                                icon = EncryptionMethod.Ark.icon,
                                label = stringResource(R.string.review_section_encryption),
                            ) {
                                Text(text = EncryptionMethod.Ark.displayName)
                            }
                    }

                    if (format == FileFormat.CSV) {
                        ReviewDivider()
                        ReviewRow(
                            icon = Icons.AutoMirrored.Default.List,
                            label = stringResource(R.string.review_section_csv_preset),
                        ) {
                            Text(text = csvPreset.displayName)
                        }
                    }
                }
            }

            if (!encrypted) ReviewPlaintextWarning()
        }
    }
}

@Composable
private fun ReviewHeroCard(format: FileFormat) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconBadge(
                icon = format.icon,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                size = 72.dp,
                iconSize = 36.dp,
            )

            Text(
                text = stringResource(R.string.review_ready_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Text(
                text = stringResource(R.string.review_ready_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ReviewRow(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    value: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IconBadge(
            icon = icon,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = iconTint,
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CompositionLocalProvider(
                LocalTextStyle provides MaterialTheme.typography.bodyLarge
            ) {
                value()
            }
        }
    }
}

@Composable
private fun ReviewDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

@Composable
private fun ReviewPlaintextWarning() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = stringResource(R.string.review_plaintext_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

private class ReviewFormatProvider : PreviewParameterProvider<FileFormat> {
    override val values = FileFormat.entries.asSequence()
}

@Preview
@Composable
private fun ReviewBackupContentPreview(
    @PreviewParameter(ReviewFormatProvider::class) format: FileFormat,
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxWidth()) {
            ReviewBackupContent(
                format = format,
                scheduleState = SelectScheduleState(
                    mode = ScheduleMode.Recurring,
                    interval = BackupInterval(count = 3, unit = IntervalUnit.Days),
                ),
                destinationState = SelectDestinationState(
                    destination = BackupDestination(
                        provider = BackupDestination.Provider.ThirdParty("Nextcloud"),
                        displayPath = "Backups / KeyGo",
                    ),
                ),
                passphraseState = ProvidePassphraseState(
                    passphraseTextFieldState = TextFieldState(),
                    confirmPassphraseTextFieldState = TextFieldState(),
                    passphraseScore = PasswordScore.Strong,
                ),
                csvPreset = CsvPreset.Browser,
            )
        }
    }
}
