package de.davis.keygo.feature.autofill.presentation

import de.davis.keygo.feature.autofill.presentation.model.FieldType

/**
 * Based of the official chromium implementations: components/autofill/core/browser/field_types.h
 * This map is NOT complete!
 */
internal val CHROMIUM_AUTOFILL_HINTS = mapOf(
    "HTML_TYPE_ONE_TIME_CODE" to FieldType.TOTP
)