package de.davis.keygo.feature.backup.domain.model

import de.davisalessandro.keygo.rust.Backup

data class CollectedBackup(
    val backup: Backup,
    val itemCount: Int,
)
