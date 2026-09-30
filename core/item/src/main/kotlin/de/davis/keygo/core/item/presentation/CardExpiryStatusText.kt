package de.davis.keygo.core.item.presentation

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import de.davis.keygo.core.item.R
import de.davis.keygo.core.item.domain.model.CardExpiryStatus

@Composable
fun CardExpiryStatusText(
    status: CardExpiryStatus,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
) {
    Text(
        text = status.label(),
        modifier = modifier,
        color = if (status.isEmphasized) MaterialTheme.colorScheme.error else Color.Unspecified,
        style = style,
    )
}

@Composable
fun CardExpiryStatus.label(): String = stringResource(
    when (this) {
        CardExpiryStatus.ExpiresThisMonth -> R.string.card_expires_this_month
        CardExpiryStatus.ExpiresNextMonth -> R.string.card_expires_next_month
        CardExpiryStatus.Expired -> R.string.card_expired
    },
)

// A card about to expire is what the user can still act on; an expired one is often kept on
// purpose, so it stays quiet instead of turning every old card red.
val CardExpiryStatus.isEmphasized: Boolean
    get() = this != CardExpiryStatus.Expired
