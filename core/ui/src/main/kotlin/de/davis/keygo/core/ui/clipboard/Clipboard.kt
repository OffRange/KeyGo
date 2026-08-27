package de.davis.keygo.core.ui.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.toClipEntry

suspend fun Clipboard.setText(label: String, text: String, sensitive: Boolean = false) {
    val clipData = ClipData.newPlainText(label, text).apply {
        if (sensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
    }

    setClipEntry(clipData.toClipEntry())
}
