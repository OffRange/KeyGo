package de.davis.keygo.feature.backup.domain.model

enum class FileFormat {
    KDBX,
    CSV;

    val recommented: Boolean
        get() = this == KDBX

    val encrypted: Boolean
        get() = this == KDBX
}