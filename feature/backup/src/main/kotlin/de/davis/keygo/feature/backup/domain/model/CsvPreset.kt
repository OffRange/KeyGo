package de.davis.keygo.feature.backup.domain.model

/** Column layout for CSV exports. */
enum class CsvPreset {
    /** Every field incl. TOTP; round-trips through KeyGo's import. */
    KeyGo,

    /** Browser-importable layout (Chrome, Edge, ...): no TOTP column. */
    Browser,
}
