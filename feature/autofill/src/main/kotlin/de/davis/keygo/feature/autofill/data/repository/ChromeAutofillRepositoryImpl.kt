package de.davis.keygo.feature.autofill.data.repository

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.util.Log
import de.davis.keygo.core.security.domain.SystemHandoff
import de.davis.keygo.core.security.domain.forRoundTrip
import de.davis.keygo.feature.autofill.domain.repository.ChromeAutofillRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

@Single
internal class ChromeAutofillRepositoryImpl(
    private val context: Context,
    private val handoff: SystemHandoff,
) : ChromeAutofillRepository {

    private val thirdPartyModeUri: Uri
        get() = Uri.Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority(CHROME_CHANNEL_PACKAGE + CONTENT_PROVIDER_NAME)
            .path(THIRD_PARTY_MODE_ACTIONS_URI_PATH)
            .build()

    private suspend fun <R> useQueryThirdPartyMode(block: (Cursor) -> R): R? =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.query(
                    thirdPartyModeUri,
                    arrayOf(THIRD_PARTY_MODE_COLUMN),
                    null,
                    null,
                    null,
                )?.use(block)
            } catch (e: RuntimeException) {
                // A provider that answers for the authority but refuses the read is
                // indistinguishable, for our purposes, from no provider at all.
                Log.w(TAG, "Failed to query Chrome's third party autofill mode provider", e)
                null
            }
        }

    override suspend fun isAvailable(): Boolean = useQueryThirdPartyMode { true } == true

    override suspend fun isAutofillEnabled(): Boolean = useQueryThirdPartyMode { cursor ->
        if (!cursor.moveToFirst()) return@useQueryThirdPartyMode false

        val thirdPartyModeState =
            cursor.getInt(cursor.getColumnIndexOrThrow(THIRD_PARTY_MODE_COLUMN))
        thirdPartyModeState == 1 // 1 means third-party autofill is enabled.
    } == true

    override fun openChromeAutofillSettings() {
        val intent = Intent(Intent.ACTION_APPLICATION_PREFERENCES).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addCategory(Intent.CATEGORY_APP_BROWSER)
            addCategory(Intent.CATEGORY_PREFERENCE)
        }

        val chooser = Intent.createChooser(intent, "Pick Chrome Channel")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        handoff.forRoundTrip { context.startActivity(chooser) }
    }

    private companion object {
        private const val TAG = "ChromeAutofillRepositoryImpl"

        private const val CHROME_CHANNEL_PACKAGE = "com.android.chrome"
        private const val CONTENT_PROVIDER_NAME = ".AutofillThirdPartyModeContentProvider"
        private const val THIRD_PARTY_MODE_COLUMN = "autofill_third_party_state"
        private const val THIRD_PARTY_MODE_ACTIONS_URI_PATH = "autofill_third_party_mode"
    }
}