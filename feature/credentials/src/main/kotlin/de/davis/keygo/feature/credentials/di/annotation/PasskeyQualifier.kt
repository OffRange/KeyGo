package de.davis.keygo.feature.credentials.di.annotation

import org.koin.core.annotation.Named
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.qualifier


@Named
@Retention(AnnotationRetention.BINARY)
internal annotation class PasskeyQualifier

internal val PasskeyProviderQualifier: Qualifier = qualifier<PasskeyQualifier>()
