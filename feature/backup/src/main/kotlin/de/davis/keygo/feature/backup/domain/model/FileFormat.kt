package de.davis.keygo.feature.backup.domain.model

enum class FileFormat(val mimeType: String, val extension: String) {
    JSON("application/json", "json"),
    CSV("text/csv", "csv");

    val recommended: Boolean
        get() = this == JSON

    val encrypted: Boolean
        get() = this == JSON

    companion object {

        /**
         * The format [fileName] implies, or null when KeyGo has no importer for it. The extension
         * decides, not the MIME type: content providers routinely report the wrong type for a CSV,
         * so the file picker cannot filter tightly enough to keep an unsupported file out.
         */
        fun fromFileName(fileName: String?): FileFormat? = fileName?.let { name ->
            entries.firstOrNull { name.endsWith(".${it.extension}", ignoreCase = true) }
        }
    }
}