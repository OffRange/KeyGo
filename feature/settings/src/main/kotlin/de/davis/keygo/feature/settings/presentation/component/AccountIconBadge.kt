package de.davis.keygo.feature.settings.presentation.component

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import de.davis.keygo.core.ui.components.IconBadge
import de.davis.keygo.feature.settings.presentation.models.AccountState
import de.davis.keygo.feature.settings.presentation.models.badgeColor

private val UsernameSeparators = charArrayOf('.', '-', '_')

internal fun accountInitials(username: String): String {
    val words = username.split(*UsernameSeparators).filter(String::isNotEmpty)
    val first = words.firstOrNull() ?: return ""
    val last = words.last()

    return if (words.size == 1) first.take(1).uppercase()
    else (first.take(1) + last.take(1)).uppercase()
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AccountIconBadge(
    state: AccountState,
    badgeSize: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    val initials = if (state is AccountState.Connected) remember(state.username) {
        accountInitials(state.username)
    } else ""

    IconBadge(
        containerColor = state.badgeColor,
        modifier = modifier,
        shape = MaterialShapes.Cookie6Sided.toShape(),
        size = badgeSize,
    ) {
        if (initials.isNotEmpty())
            Initials(initials = initials, size = iconSize)
        else
            Icon(
                imageVector = if (state is AccountState.Connected) Icons.Default.Person else Icons.Default.Smartphone,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
            )
    }
}

@Composable
private fun Initials(initials: String, size: Dp) {
    val fontSize = with(LocalDensity.current) { size.toSp() }

    Text(
        text = initials,
        style = MaterialTheme.typography.titleMedium.copy(
            fontSize = fontSize,
            lineHeight = fontSize,
            letterSpacing = 0.sp,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
        ),
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.clearAndSetSemantics {},
    )
}
