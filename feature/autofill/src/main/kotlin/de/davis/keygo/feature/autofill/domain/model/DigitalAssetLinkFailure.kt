package de.davis.keygo.feature.autofill.domain.model

sealed interface DigitalAssetLinkFailure {

    data object Unreachable : DigitalAssetLinkFailure
    data object NoVerdict : DigitalAssetLinkFailure
}
