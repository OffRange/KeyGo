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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.R
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.model.lite.LitePassword
import de.davis.keygo.core.ui.theme.KeyGoTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Composable
fun SelectItemForTotpModificationDialog(
    onDismissRequest: () -> Unit,
    items: ImmutableList<LitePassword>,
    onItemClicked: (LitePassword) -> Unit,
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

                                    Text(
                                        text = buildAnnotatedString {
                                            item.domains.take(3).joinToString { it.value }.also {
                                                append(stringResource(R.string.list_entry, it))
                                            }

                                            if (item.domains.size <= 3)
                                                return@buildAnnotatedString

                                            append(", ")
                                            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                                append(
                                                    stringResource(
                                                        R.string.n_more,
                                                        item.domains.size - 3
                                                    )
                                                )
                                            }
                                        }
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
private fun SelectItemForTotpModificationDialogPreview() {
    KeyGoTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            SelectItemForTotpModificationDialog(
                onDismissRequest = {},
                onItemClicked = {},
                onCreateNew = {},
                items = persistentListOf(
                    LitePassword(
                        vaultItemId = 1,
                        passwordId = 1,
                        name = "${if (1 >= 5) 'A' else 'B'} Item 1",
                        username = "User 1",
                        domains = listOf(DomainInfo(1, "Website", "website.com")),
                    ),
                    LitePassword(
                        vaultItemId = 2,
                        passwordId = 2,
                        name = "${if (2 >= 5) 'A' else 'B'} Item 2",
                        username = "User 2",
                        domains = listOf(DomainInfo(12, "Website", "website.com")),
                    )
                )
            )
        }
    }
}