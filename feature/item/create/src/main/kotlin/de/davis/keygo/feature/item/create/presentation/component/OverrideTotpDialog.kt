package de.davis.keygo.feature.item.create.presentation.component

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.ui.theme.KeyGoTheme
import de.davis.keygo.feature.item.core.presentation.login.model.FieldType
import de.davis.keygo.feature.item.create.R
import de.davis.keygo.feature.item.create.presentation.login.model.OverrideTotpField

@Composable
fun OverrideTotpDialog(
    onDismissRequest: () -> Unit,
    onOverride: () -> Unit,
    onKeep: () -> Unit,
    onFieldClicked: (FieldType) -> Unit,
    overrideFields: Set<OverrideTotpField>,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        confirmButton = {
            Button(
                onClick = onOverride
            ) {
                AnimatedContent(overrideFields.any { it.selected }) {
                    when (it) {
                        true -> Text(text = stringResource(R.string.override))
                        false -> Text(text = stringResource(R.string.keep))
                    }
                }
            }
        },
        dismissButton = overrideFields
            .takeIf { it.any { field -> field.selected } }
            ?.let {
                {
                    OutlinedButton(
                        onClick = onKeep
                    ) {
                        Text(text = stringResource(R.string.keep))
                    }
                }
            },
        icon = {
            Icon(
                imageVector = Icons.Default.TextFields,
                contentDescription = null
            )
        },
        title = {
            Text(text = stringResource(R.string.override_totp_fields))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = stringResource(R.string.overriding_totp_fields_description))
                HorizontalDivider()
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = overrideFields.toList(),
                        key = { it.fieldType }
                    ) { item ->
                        Card(
                            onClick = {
                                onFieldClicked(item.fieldType)
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = Color.Transparent
                            )
                        ) {
                            MultiSupportingLineItem(
                                headlineContent = {
                                    Text(item.fieldType.toString())
                                },
                                supportingContent = {
                                    Text(text = stringResource(R.string.list_before, item.before))
                                    Text(text = stringResource(R.string.list_after, item.after))

                                },
                                leadingContent = {
                                    Checkbox(
                                        checked = item.selected,
                                        onCheckedChange = null
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    )
}


@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OverrideTotpDialogPreview() {
    KeyGoTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            OverrideTotpDialog(
                onDismissRequest = {},
                onOverride = {},
                onKeep = {},
                onFieldClicked = {},
                overrideFields = setOf(
                    OverrideTotpField(
                        fieldType = FieldType.Totp,
                        before = "oldTotpSecret",
                        after = "newTotpSecret",
                        selected = true
                    ),
                    OverrideTotpField(
                        fieldType = FieldType.Username,
                        before = "oldUsername",
                        after = "newUsername",
                        selected = false
                    ),
                    OverrideTotpField(
                        fieldType = FieldType.Domain,
                        before = "oldWebsite",
                        after = "newWebsite",
                        selected = true
                    )
                )
            )
        }
    }
}