package de.davis.keygo.feature.list_screen.presentation.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import de.davis.keygo.core.item.domain.model.lite.LiteItemSearchResult
import de.davis.keygo.feature.list_screen.R

internal enum class SearchMatchField {
    Name,
    Username,
    Note,
    Tag;

    val label: String
        @Composable get() = stringResource(
            when (this) {
                Name -> R.string.search_match_name
                Username -> R.string.search_match_username
                Note -> R.string.search_match_note
                Tag -> R.string.search_match_tag
            }
        )

    val icon: ImageVector
        get() = when (this) {
            Name -> Icons.Default.TextFields
            Username -> Icons.Default.AlternateEmail
            Note -> Icons.AutoMirrored.Filled.Notes
            Tag -> Icons.Default.Sell
        }
}

internal fun LiteItemSearchResult.matchedFields(): List<SearchMatchField> = buildList {
    if (matchedName) add(SearchMatchField.Name)
    if (matchedUsername) add(SearchMatchField.Username)
    if (matchedNote) add(SearchMatchField.Note)
    if (matchedTag) add(SearchMatchField.Tag)
}
