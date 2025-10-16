package de.davis.keygo.autofill.domain

interface SignatureInfoProvider {

    fun getSignatureInfo(packageName: String): Set<String>
}