package de.davis.keygo.feature.backup.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.davis.keygo.feature.backup.R
import de.davis.keygo.feature.backup.domain.model.BackupDestination

@Composable
internal fun BackupFileChooser(
    destination: BackupDestination?,
    onChoose: () -> Unit,
    chooserIcon: ImageVector,
    chooserTitle: String,
    chooserSubtitle: String,
    chooserAction: String,
    changeLabel: String,
    fileNameLabel: String,
    modifier: Modifier = Modifier,
) {
    when (destination) {
        null -> ChooserCard(
            icon = chooserIcon,
            title = chooserTitle,
            subtitle = chooserSubtitle,
            action = chooserAction,
            onChoose = onChoose,
            modifier = modifier,
        )

        else -> SelectedCard(
            destination = destination,
            changeLabel = changeLabel,
            fileNameLabel = fileNameLabel,
            onChange = onChoose,
            modifier = modifier,
        )
    }
}

@Composable
private fun ChooserCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    action: String,
    onChoose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onChoose,
        modifier = modifier
            .fillMaxWidth()
            .dashedBorder(
                width = 1.dp,
                color = MaterialTheme.colorScheme.secondary,
                shape = CardDefaults.shape,
            ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null)
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
            Text(text = action, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SelectedCard(
    destination: BackupDestination,
    changeLabel: String,
    fileNameLabel: String,
    onChange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        onClick = onChange,
        shapes = ListItemDefaults.shapes(shape = MaterialTheme.shapes.large),
        modifier = modifier.fillMaxWidth(),
        colors = ListItemDefaults.segmentedColors(containerColor = segmentContainerColor),
        leadingContent = {
            Icon(
                imageVector = if (destination.fileName != null) Icons.Default.Description
                else Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        overlineContent = { Text(text = destination.provider.label) },
        supportingContent = {
            Text(
                text = fileNameLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        // Not a button: the whole row already invokes onChange, and a nested control would give
        // one action two focus stops and two ripples. This is the affordance's label.
        trailingContent = {
            Text(
                text = changeLabel,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = destination.displayPath,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun Modifier.dashedBorder(
    width: Dp,
    color: Color,
    shape: Shape,
    on: Dp = 6.dp,
    off: Dp = 4.dp,
) = drawWithContent {
    drawContent()
    drawOutline(
        outline = shape.createOutline(size, layoutDirection, this),
        color = color,
        style = Stroke(
            width = width.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(on.toPx(), off.toPx())),
        ),
    )
}

private val BackupDestination.Provider.label
    @Composable
    get() = when (this) {
        BackupDestination.Provider.Unknown -> stringResource(R.string.destination_provider_unknown)
        BackupDestination.Provider.OnDevice -> stringResource(R.string.destination_provider_on_device)
        is BackupDestination.Provider.ThirdParty -> name
    }
