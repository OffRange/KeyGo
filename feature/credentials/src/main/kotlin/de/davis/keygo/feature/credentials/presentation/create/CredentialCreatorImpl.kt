package de.davis.keygo.feature.credentials.presentation.create

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.credentials.provider.CreateEntry
import de.davis.keygo.feature.credentials.R
import org.koin.core.annotation.Single

@Single
internal class CredentialCreatorImpl(
    private val applicationContext: Context
) : CredentialCreator {

    override fun create(): CreateEntry = CreateEntry(
        accountName = applicationContext.getString(R.string.default_account),
        pendingIntent = getPendingIntent()
    )

    private fun getPendingIntent(): PendingIntent = Intent(ACTION_CREATE_PASSKEY).apply {
        `package` = applicationContext.packageName
        // TODO: Add when introducing multiple accounts
        //  putExtra(EXTRA_KEY_ACCOUNT_ID, accountId)
        //  also make requestCodes unique per account (see PendingIntent below)
    }.let {
        PendingIntent.getActivity(
            applicationContext,
            1001,
            it,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    companion object {
        private const val ACTION_CREATE_PASSKEY =
            "de.davis.keygo.feature.credentials.action.CREATE_PASSKEY"
    }
}