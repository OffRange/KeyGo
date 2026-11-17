package de.davis.keygo.feature.settings.presentation.account.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlexBox
import androidx.compose.foundation.layout.FlexBoxConfig
import androidx.compose.foundation.layout.FlexConfig
import androidx.compose.foundation.layout.FlexWrap
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.ui.components.IconBadge
import de.davis.keygo.feature.settings.R

@Composable
fun ConnectToServerCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.account_connect_to_server),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = stringResource(R.string.account_connect_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ServerFeature(
                    icon = Icons.Outlined.Shield,
                    text = stringResource(R.string.account_e2e),
                )
                ServerFeature(
                    icon = Icons.Outlined.CloudOff,
                    text = stringResource(R.string.account_offline_first),
                )
                ServerFeature(
                    icon = Icons.Outlined.Dns,
                    text = stringResource(R.string.account_self_hostable),
                )
            }

            Buttons()
        }
    }
}

@Composable
private fun Buttons(
    modifier: Modifier = Modifier,
    buttonSize: Dp = ButtonDefaults.MediumContainerHeight
) {
    val shapes = ButtonDefaults.shapesFor(buttonSize)
    val contentPadding = ButtonDefaults.contentPaddingFor(buttonSize)
    val textStyle = ButtonDefaults.textStyleFor(buttonSize)

    FlexBox(
        modifier = modifier.fillMaxWidth(),
        config = ServerButtonsConfig,
    ) {
        FilledTonalButton(
            onClick = {},
            modifier = Modifier
                .heightIn(min = buttonSize)
                .flex(HalfWidthButton),
            shapes = shapes,
            contentPadding = contentPadding,
        ) {
            Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = null,
                modifier = Modifier.size(
                    ButtonDefaults.iconSizeFor(
                        buttonSize
                    )
                ),
            )
            Spacer(
                modifier = Modifier.width(
                    ButtonDefaults.iconSpacingFor(
                        buttonSize
                    )
                )
            )
            Text(
                text = stringResource(R.string.account_scan_invite),
                style = textStyle,
                maxLines = 1,
                softWrap = false,
            )
        }

        Button(
            onClick = {},
            modifier = Modifier
                .heightIn(min = buttonSize)
                .flex(HalfWidthButton),
            shapes = shapes,
            contentPadding = contentPadding,
        ) {
            Text(
                text = stringResource(R.string.account_connect),
                style = textStyle,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun ServerFeature(icon: ImageVector, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = ListItemDefaults.verticalAlignment(),
    ) {
        IconBadge(
            icon = icon,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            size = 36.dp,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private val ServerButtonGap = 8.dp

private val ServerButtonsConfig = FlexBoxConfig {
    wrap(FlexWrap.Wrap)
    gap(ServerButtonGap)
}

// Half the row as the basis; FlexBox never goes below a button's min intrinsic width, so a label
// that doesn't fit in half pushes the pair onto two lines instead of truncating.
private val HalfWidthButton = FlexConfig {
    grow(1f)
    basis(((flexBoxMainAxisMax - ServerButtonGap.roundToPx()) / 2).toDp())
}

@Preview
@Composable
private fun ConnectToServerCardPreview() {
    MaterialTheme {
        ConnectToServerCard()
    }
}