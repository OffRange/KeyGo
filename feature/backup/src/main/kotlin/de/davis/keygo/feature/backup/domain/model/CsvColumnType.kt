package de.davis.keygo.feature.backup.domain.model

/** A data type the user can assign to a CSV column. Absence (null) means "Ignore". */
enum class CsvColumnType {
    Title,
    Url,
    Username,
    Password,
    Notes,
    Totp,
}
