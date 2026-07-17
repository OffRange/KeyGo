package de.davis.keygo.feature.backup.domain.model

enum class FileFormat(val mimeType: String, val extension: String) {
    JSON("application/json", "json"),
    CSV("text/csv", "csv");

    val recommented: Boolean
        get() = this == JSON

    val encrypted: Boolean
        get() = this == JSON
}