package de.davis.keygo.autofill.presentation.dataset.inline

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.autofill.InlinePresentation
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import androidx.autofill.inline.v1.InlineSuggestionUi
import org.koin.core.annotation.Single

@Single
class InlineSuggestionFactory {

    @RequiresApi(Build.VERSION_CODES.R)
    fun buildPresentation(
        spec: InlinePresentationSpec,
        pendingIntent: PendingIntent,
        title: String,
        subtitle: String? = null,
        icon: Icon? = null
    ): InlinePresentation = buildUniversalPresentation(
        spec = spec,
        pendingIntent = pendingIntent,
        title = title,
        subtitle = subtitle,
        icon = icon
    )

    @RequiresApi(Build.VERSION_CODES.R)
    fun buildPinnedPresentation(
        spec: InlinePresentationSpec,
        pendingIntent: PendingIntent,
        icon: Icon? = null
    ): InlinePresentation = buildUniversalPresentation(
        spec = spec,
        pendingIntent = pendingIntent,
        icon = icon,
        pinned = true
    )

    @SuppressLint("RestrictedApi")
    @RequiresApi(Build.VERSION_CODES.R)
    private fun buildUniversalPresentation(
        spec: InlinePresentationSpec,
        pendingIntent: PendingIntent,
        title: String? = null,
        subtitle: String? = null,
        icon: Icon? = null,
        pinned: Boolean = false
    ): InlinePresentation {
        val content = InlineSuggestionUi.newContentBuilder(pendingIntent).apply {
            title?.let {
                setContentDescription(it)
                setTitle(it)
            }

            subtitle?.let {
                setSubtitle(it)
            }

            icon?.let {
                setStartIcon(it)
            }
        }.build()

        return InlinePresentation(
            content.slice,
            spec,
            pinned
        )
    }
}