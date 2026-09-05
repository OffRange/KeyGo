package de.davis.keygo.feature.autofill.presentation.activity.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.ui.text.htmlStringResource
import de.davis.keygo.feature.autofill.R

/**
 * Shown while the digital asset link lookup for [website] is still in flight. The lookup is a
 * network call, so without this the transparent autofill activity would sit there showing nothing.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LinkCheckPendingDialog(
    website: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            TextButton(
                onClick = onCancel
            ) {
                Text(text = stringResource(R.string.cancel))
            }
        },
        icon = {
            Icon(imageVector = Icons.Default.Link, contentDescription = null)
        },
        title = {
            Text(text = stringResource(R.string.checking_website_link))
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = htmlStringResource(
                        R.string.checking_website_link_description,
                        website,
                    )
                )
                LoadingIndicator()
            }
        },
        modifier = modifier
    )
}

@Preview
@Composable
private fun LinkCheckPendingDialogPreview() {
    MaterialTheme {
        LinkCheckPendingDialog(
            website = "example.com",
            onCancel = {},
            modifier = Modifier.fillMaxWidth()
        )
    }
}
