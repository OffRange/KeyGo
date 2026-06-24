package de.davis.keygo.feature.backup.domain.model

enum class FileFormat(val mimeType: String) {
    JSON("application/json"),
    CSV("text/csv");

    val recommented: Boolean
        get() = this == JSON

    val encrypted: Boolean
        get() = this == JSON
}