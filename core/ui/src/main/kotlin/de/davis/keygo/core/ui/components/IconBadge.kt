package de.davis.keygo.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun IconBadge(
    icon: ImageVector,
    containerColor: Color,
    modifier: Modifier = Modifier,
    contentColor: Color = contentColorFor(containerColor),
    shape: Shape = CircleShape,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
) {
    IconBadge(
        containerColor = containerColor,
        contentColor = contentColor,
        modifier = modifier,
        shape = shape,
        size = size,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
fun IconBadge(
    containerColor: Color,
    modifier: Modifier = Modifier,
    contentColor: Color = contentColorFor(containerColor),
    shape: Shape = CircleShape,
    size: Dp = 40.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.size(size),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}
