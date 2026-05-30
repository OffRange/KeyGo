package de.davis.keygo.feature.item.view.creditcard.model

import de.davis.keygo.core.item.domain.model.Tag
import de.davis.keygo.feature.item.core.presentation.model.InputFieldError

data class ModificationDialog(
    val fieldType: CreditCardFieldType,
    val initialValue: String,
    val error: InputFieldError? = null,
    val tagsToSuggest: Set<Tag> = emptySet(),
)
