package de.davis.keygo.core.security.domain.model

data class BiometricPolicy(
    val title: BiometricString.Title = BiometricString.Title.Authenticate,
    val negativeButton: BiometricString.NegativeButton = BiometricString.NegativeButton.Cancel
) {
    companion object {
        val Default = BiometricPolicy()
    }
}