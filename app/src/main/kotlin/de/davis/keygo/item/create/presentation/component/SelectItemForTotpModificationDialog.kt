package de.davis.keygo.item.create.presentation.component

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.R
import de.davis.keygo.core.domain.model.Password
import de.davis.keygo.core.domain.model.Score
import de.davis.keygo.core.domain.model.crypto.CryptographicData
import de.davis.keygo.core.presentation.theme.KeyGoTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Composable
fun SelectItemForTotpModificationDialog(
    onDismissRequest: () -> Unit,
    items: ImmutableList<Password>,
    onItemClicked: (Password) -> Unit,
    onCreateNew: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        confirmButton = {
            OutlinedButton(
                onClick = onCreateNew
            ) {
                Text(text = stringResource(R.string.create_new))
            }
        },
        icon = {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null
            )
        },
        title = {
            Text(text = stringResource(R.string.existing_entries_found))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = stringResource(R.string.existing_entries_found_description))
                HorizontalDivider()
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items = items, key = { it.vaultItemId }) { item ->
                        OutlinedCard(
                            onClick = {
                                onItemClicked(item)
                            },
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = Color.Transparent
                            )
                        ) {
                            MultiSupportingLineItem(
                                headlineContent = {
                                    Text(text = item.name)
                                },
                                supportingContent = {
                                    item.username?.let {
                                        Text(text = stringResource(R.string.list_entry, it))
                                    }

                                    item.website?.let {
                                        Text(text = stringResource(R.string.list_entry, it))
                                    }
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
private fun SelectItemForTotpModificationDialogPreview() {
    KeyGoTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            SelectItemForTotpModificationDialog(
                onDismissRequest = {},
                onItemClicked = {},
                onCreateNew = {},
                items = persistentListOf(
                    Password(
                        passwordId = 1,
                        username = "User 1",
                        website = "Website",
                        score = Score.Weak,
                        vaultItemId = 1,
                        name = "${if (1 >= 5) 'A' else 'B'} Item 1",
                        encryptedData = CryptographicData.EMPTY,
                        note = "This is a note for item 1",
                        totpSecret = null
                    ), Password(
                        passwordId = 2,
                        username = "User 2",
                        website = "Website",
                        score = Score.Weak,
                        vaultItemId = 2,
                        name = "${if (2 >= 5) 'A' else 'B'} Item 2",
                        encryptedData = CryptographicData.EMPTY,
                        note = "This is a note for item 2",
                        totpSecret = null
                    )
                )
            )
        }
    }
}