package de.davis.keygo.feature.backup.domain.model

enum class CsvPreset {
    /** Every field incl. TOTP; round-trips through KeyGo's import. */
    KeyGo,

    /** Browser-importable layout (Chrome, Edge, ...): no TOTP column. */
    Browser,
}
