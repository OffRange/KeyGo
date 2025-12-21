package de.davis.keygo.core.util.presentation

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

sealed interface UIText {
    data class RawString(val string: String) : UIText
    data class ResourceString(@StringRes val resId: Int, val args: List<Any> = emptyList()) : UIText
    data class PluralsString(
        @PluralsRes val resId: Int,
        val count: Int,
        val args: List<Any> = emptyList()
    ) : UIText

    companion object {
        fun ResourceString(@StringRes resId: Int, vararg args: Any) =
            UIText.ResourceString(resId, args.toList())

        fun PluralsString(@PluralsRes resId: Int, count: Int, vararg args: Any) =
            UIText.PluralsString(resId, count, args.toList())
    }
}

@Composable
fun UIText.asString() = when (this) {
    is UIText.RawString -> string
    is UIText.ResourceString -> stringResource(resId, *args.toTypedArray())
    is UIText.PluralsString -> pluralStringResource(resId, count, *args.toTypedArray())
}

fun UIText.resolve(context: Context) = when (this) {
    is UIText.RawString -> string
    is UIText.ResourceString -> context.getString(resId, *args.toTypedArray())
    is UIText.PluralsString -> context.resources.getQuantityString(
        resId,
        count,
        *args.toTypedArray()
    )
}

fun String.asUIText() = UIText.RawString(this)