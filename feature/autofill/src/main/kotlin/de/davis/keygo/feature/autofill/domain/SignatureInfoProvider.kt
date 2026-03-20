package de.davis.keygo.feature.autofill.domain

interface SignatureInfoProvider {

    fun getSignatureInfo(packageName: String): Set<String>
}