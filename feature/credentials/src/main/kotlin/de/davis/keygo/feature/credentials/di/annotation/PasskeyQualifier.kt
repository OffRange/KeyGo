package de.davis.keygo.feature.credentials.di.annotation

import org.koin.core.annotation.Named


@Named
internal annotation class PasskeyQualifier {

    companion object {
        const val NAMED_QUALIFIER = "PasskeyQualifier"
    }
}