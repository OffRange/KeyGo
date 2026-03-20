package de.davis.keygo.feature.autofill.presentation.dataset.menu

import android.content.Context
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import de.davis.keygo.feature.autofill.R
import org.koin.core.annotation.Single

@Single
internal class MenuSuggestionFactory(
    private val context: Context
) {

    fun buildMenuSuggestion(
        title: String,
        subtitle: String? = null,
        @DrawableRes
        icon: Int? = null
    ): RemoteViews = RemoteViews(
        context.packageName,
        R.layout.autofill_menu
    ).apply {
        setTextViewText(R.id.title, title)
        subtitle?.let { setTextViewText(R.id.subtitle, it) }
        icon?.let { setImageViewResource(R.id.icon, it) }
    }
}