package de.davis.keygo.feature.list_screen.presentation.model

import androidx.compose.runtime.Immutable
import de.davis.keygo.core.item.domain.model.CardExpiryStatus
import de.davis.keygo.core.item.domain.model.CredentialType
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.domain.model.Tag
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.feature.list_screen.domain.model.SortDirection

@Immutable
internal data class FilterBottomSheetState(
    val sortDirection: SortDirection = SortDirection.Ascending,
    val itemSection: ItemSectionState? = null,
    val loginSection: LoginSectionState? = null,
    val creditCardSection: CreditCardSectionState? = null,
    val isDefault: Boolean = true,
    val isVisible: Boolean = false,
)

@Immutable
internal data class ItemSectionState(
    val onlyPinned: FilterChipState<Boolean>?,
    val itemTypeChips: List<FilterChipState<VaultItemType>>,
    val tagChips: List<FilterChipState<Tag>>,
)

@Immutable
internal data class LoginSectionState(
    val passwordScoreChips: List<FilterChipState<PasswordScore>>,
    val credentialChips: List<FilterChipState<CredentialType>>,
)

@Immutable
internal data class CreditCardSectionState(
    val expiryStatusChips: List<FilterChipState<CardExpiryStatus>>,
)

@Immutable
internal data class FilterChipState<out T>(
    val value: T,
    val selected: Boolean,
)
