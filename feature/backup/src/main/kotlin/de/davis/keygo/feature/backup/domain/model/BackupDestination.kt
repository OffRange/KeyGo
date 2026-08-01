package de.davis.keygo.feature.backup.domain.model

data class BackupDestination(
    val provider: Provider,
    val displayPath: String,
    val fileName: String? = null,
) {

    sealed interface Provider {
        data object Unknown : Provider
        data object OnDevice : Provider
        data class ThirdParty(val name: String) : Provider
    }
}