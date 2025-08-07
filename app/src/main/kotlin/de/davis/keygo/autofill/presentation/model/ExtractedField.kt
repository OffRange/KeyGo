package de.davis.keygo.autofill.presentation.model

import android.view.autofill.AutofillId
import de.davis.keygo.autofill.domain.alias.Handle
import de.davis.keygo.autofill.domain.model.FieldFeatures
import de.davis.keygo.autofill.domain.model.FieldType

data class ExtractedField(
    val handle: Handle,
    val autofillId: AutofillId,
    val features: FieldFeatures,
    val type: FieldType,
)