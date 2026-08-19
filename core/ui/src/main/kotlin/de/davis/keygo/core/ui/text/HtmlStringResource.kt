package de.davis.keygo.core.ui.text

import android.text.TextUtils
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml

/**
 * Resolves the HTML-formatted string resource [id] and parses its markup into an [AnnotatedString].
 *
 * Every entry of [formatArgs] is HTML-escaped before it is substituted, so only the resource itself
 * can contribute markup. An argument that is not ours to trust, such as a relying party id taken
 * verbatim from an incoming credential request, therefore renders as plain text instead of turning
 * into a link or breaking out of the resource's own formatting.
 */
@Composable
@ReadOnlyComposable
fun htmlStringResource(@StringRes id: Int, vararg formatArgs: String): AnnotatedString =
    AnnotatedString.fromHtml(
        stringResource(id, *Array(formatArgs.size) { TextUtils.htmlEncode(formatArgs[it]) }),
    )
