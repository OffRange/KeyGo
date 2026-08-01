package de.davis.keygo.feature.autofill.data.repository

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import de.davis.keygo.feature.autofill.domain.repository.ChromeAutofillRepository
import org.koin.core.annotation.Single

@Single
internal class ChromeAutofillRepositoryImpl(
    private val context: Context,
) : ChromeAutofillRepository {

    override fun isAutofillEnabled(): Boolean {
        val uri = Uri.Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority(CHROME_CHANNEL_PACKAGE + CONTENT_PROVIDER_NAME)
            .path(THIRD_PARTY_MODE_ACTIONS_URI_PATH)
            .build()

        return context.contentResolver.query(
            uri,
            arrayOf(THIRD_PARTY_MODE_COLUMN),
            null,
            null,
            null,
        )?.use {
            if (!it.moveToFirst()) return false

            val thirdPartyModeState = it.getInt(it.getColumnIndexOrThrow(THIRD_PARTY_MODE_COLUMN))
            thirdPartyModeState == 1 // 1 means third-party autofill is enabled.
        } == true
    }

    override fun openChromeAutofillSettings() {
        val intent = Intent(Intent.ACTION_APPLICATION_PREFERENCES).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addCategory(Intent.CATEGORY_APP_BROWSER)
            addCategory(Intent.CATEGORY_PREFERENCE)
        }

        val chooser = Intent.createChooser(intent, "Pick Chrome Channel")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    private companion object {
        private val CHROME_CHANNEL_PACKAGE = "com.android.chrome" // Chrome Stable.
        private val CONTENT_PROVIDER_NAME = ".AutofillThirdPartyModeContentProvider"
        private val THIRD_PARTY_MODE_COLUMN = "autofill_third_party_state"
        private val THIRD_PARTY_MODE_ACTIONS_URI_PATH = "autofill_third_party_mode"
    }
}