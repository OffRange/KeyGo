package de.davis.keygo.feature.credentials.presentation.provide

import android.app.PendingIntent
import android.content.Context
import androidx.credentials.provider.BeginGetPublicKeyCredentialOption
import androidx.credentials.provider.CredentialEntry
import androidx.credentials.provider.PublicKeyCredentialEntry
import de.davis.keygo.core.item.domain.model.PasskeyMetadata
import de.davis.keygo.feature.credentials.di.annotation.PasskeyQualifier
import de.davis.keygo.feature.credentials.domain.usecase.GetPasskeys
import de.davis.keygo.feature.credentials.presentation.provide.activity.ProvidePasskeyActivity
import org.koin.core.annotation.Single

@Single
@PasskeyQualifier
internal class PasskeyCredentialProviderImpl(
    private val applicationContext: Context,
    private val getPasskeys: GetPasskeys
) : CredentialProvider<BeginGetPublicKeyCredentialOption> {

    override suspend fun provideFor(option: BeginGetPublicKeyCredentialOption): List<CredentialEntry> {
        return getPasskeys(option.requestJson).map {
            PublicKeyCredentialEntry.Builder(
                context = applicationContext,
                username = it.username(),
                pendingIntent = getPendingIntent(it),
                beginGetPublicKeyCredentialOption = option
            ).setDisplayName(it.displayName()).build()
        }
    }

    private fun PasskeyMetadata.username(): String {
        if (user.name.equals(passwordUsername, ignoreCase = true)
            || passwordUsername.isNullOrBlank()
        ) return user.name

        return "${user.name} \u2022 $passwordUsername"
    }

    private fun PasskeyMetadata.displayName(): String {
        return "${user.displayName} \u2022 $vaultName"
    }

    private fun getPendingIntent(passkeyMetadata: PasskeyMetadata): PendingIntent =
        ProvidePasskeyActivity.getIntent(passkeyMetadata.credentialId, applicationContext).let {
            PendingIntent.getActivity(
                applicationContext,
                1001,
                it,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
}

