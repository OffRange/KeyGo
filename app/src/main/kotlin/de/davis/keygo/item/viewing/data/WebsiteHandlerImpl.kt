package de.davis.keygo.item.viewing.data

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import de.davis.keygo.item.viewing.domain.WebsiteHandler
import org.koin.core.annotation.Single

@Single
class WebsiteHandlerImpl(
    private val context: Context
) : WebsiteHandler {

    override fun openWebsite(url: String) {
        Intent(Intent.ACTION_VIEW, url.ensureProtocol().toUri()).let {
            runCatching {
                context.startActivity(it)
            }
        }
    }

    private fun String.ensureProtocol(): String =
        if (startsWith("http://") || startsWith("https://")) this
        else "https://$this"
}