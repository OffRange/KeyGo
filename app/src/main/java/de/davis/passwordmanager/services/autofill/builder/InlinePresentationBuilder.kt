package de.davis.passwordmanager.services.autofill.builder

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.autofill.InlinePresentation
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import androidx.autofill.inline.v1.InlineSuggestionUi

@SuppressLint("RestrictedApi")
@RequiresApi(Build.VERSION_CODES.R)
object InlinePresentationBuilder {

    fun createBasicPresentation(
        title: String,
        subtitle: String? = null,
        icon: Icon? = null,
        inlinePresentationSpec: InlinePresentationSpec,
        pendingIntent: PendingIntent
    ): InlinePresentation {
        return InlinePresentation(
            InlineSuggestionUi.newContentBuilder(pendingIntent).apply {
                setContentDescription("$title $subtitle")
                setTitle(title)
                if (subtitle != null)
                    setSubtitle(subtitle)

                if (icon != null)
                    setStartIcon(icon)
            }.build().slice,
            inlinePresentationSpec,
            false
        )
    }

    fun createPinnedPresentation(
        icon: Icon? = null,
        inlinePresentationSpec: InlinePresentationSpec,
        pendingIntent: PendingIntent
    ): InlinePresentation {
        return InlinePresentation(
            InlineSuggestionUi.newContentBuilder(pendingIntent).apply {
                setContentDescription("Pinned Autofill option")
                if (icon != null)
                    setStartIcon(icon)
            }.build().slice,
            inlinePresentationSpec,
            true
        )
    }
}