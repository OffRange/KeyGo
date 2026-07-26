package de.davis.keygo.feature.backup.presentation.export

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Password
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.item.presentation.StrengthIndicator
import de.davis.keygo.core.ui.components.VisibilityButton
import de.davis.keygo.feature.backup.R
import de.davis.keygo.feature.backup.domain.model.EncryptionMethod
import de.davis.keygo.feature.backup.presentation.component.BackupWarningCard
import de.davis.keygo.feature.backup.presentation.component.segmentContainerColor
import de.davis.keygo.feature.backup.presentation.description
import de.davis.keygo.feature.backup.presentation.displayName
import de.davis.keygo.feature.backup.presentation.export.model.ExportWizardUiEvent
import de.davis.keygo.feature.backup.presentation.export.model.ProvidePassphraseState
import de.davis.keygo.feature.backup.presentation.icon

@Composable
internal fun ProvidePassphraseContent(
    state: ProvidePassphraseState,
    onEvent: (ExportWizardUiEvent) -> Unit,
) {
    var passphraseHidden by rememberSaveable { mutableStateOf(true) }
    var confirmPassphraseHidden by rememberSaveable { mutableStateOf(true) }
    var forceCompact by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        EncryptionMethod.entries.forEachIndexed { index, method ->
            SegmentedListItem(
                onClick = { onEvent(ExportWizardUiEvent.EncryptionMethodSelected(method)) },
                shapes = ListItemDefaults.segmentedShapes(index, EncryptionMethod.entries.size),
                colors = ListItemDefaults.segmentedColors(
                    containerColor = if (state.method == method) MaterialTheme.colorScheme.secondaryContainer
                    else segmentContainerColor,
                    contentColor = contentColorFor(
                        if (state.method == method) MaterialTheme.colorScheme.secondaryContainer
                        else segmentContainerColor
                    ),
                ),
                supportingContent = {
                    Text(text = method.description)
                },
                leadingContent = {
                    Icon(
                        imageVector = method.icon,
                        contentDescription = null,
                    )
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = method.displayName)
            }
        }

        // The cost of this method is not visible in its own description, and it only lands once
        // the method is actually chosen, so it warns here rather than in the option's subtitle.
        AnimatedVisibility(visible = state.method == EncryptionMethod.Ark) {
            BackupWarningCard(
                text = stringResource(R.string.encryption_method_ark_warning),
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        AnimatedVisibility(visible = state.method == EncryptionMethod.Passphrase) {
            // The segments above now sit at SegmentedGap, so the form needs its own breathing
            // room rather than inheriting the gap that separates one segment from the next.
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.export_passphrase_instruction),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                PassphraseField(
                    state = state.passphraseTextFieldState,
                    label = stringResource(R.string.passphrase),
                    hidden = passphraseHidden,
                    onToggleHidden = { passphraseHidden = !passphraseHidden },
                    modifier = Modifier.onFocusChanged { forceCompact = !it.hasFocus },
                )
                StrengthIndicator(
                    passwordScore = state.passphraseScore,
                    forceCompact = forceCompact,
                )

                PassphraseField(
                    state = state.confirmPassphraseTextFieldState,
                    label = stringResource(R.string.confirm_passphrase),
                    hidden = confirmPassphraseHidden,
                    onToggleHidden = { confirmPassphraseHidden = !confirmPassphraseHidden },
                )
            }
        }
    }
}

@Composable
private fun PassphraseField(
    state: TextFieldState,
    label: String,
    hidden: Boolean,
    onToggleHidden: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedSecureTextField(
        state = state,
        modifier = modifier.fillMaxWidth(),
        label = {
            Text(text = label)
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Password,
                contentDescription = null,
            )
        },
        textObfuscationMode = if (hidden) TextObfuscationMode.RevealLastTyped
        else TextObfuscationMode.Visible,
        trailingIcon = {
            VisibilityButton(
                isHidden = hidden,
                onClick = onToggleHidden,
            )
        },
    )
}
