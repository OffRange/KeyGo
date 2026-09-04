package de.davis.keygo.feature.item.view.data

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import de.davis.keygo.core.security.domain.SystemHandoff
import de.davis.keygo.core.security.domain.forRoundTrip
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.feature.item.view.domain.WebsiteHandler
import org.koin.core.annotation.Single

@Single
internal class WebsiteHandlerImpl(
    private val context: Context,
    private val handoff: SystemHandoff,
) : WebsiteHandler {

    override fun openWebsite(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.ensureProtocol().toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        handoff.forRoundTrip { context.startActivity(intent) }
            .onFailure { Log.w(TAG, "Failed to open a website", it) }
    }

    private fun String.ensureProtocol(): String =
        if (startsWith("http://") || startsWith("https://")) this
        else "https://$this"

    private companion object {
        private const val TAG = "WebsiteHandlerImpl"
    }
}
