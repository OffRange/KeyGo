package de.davis.keygo.feature.item.core.presentation.component

import android.content.ClipData
import android.content.ClipDescription
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import de.davis.keygo.core.ui.R
import kotlinx.coroutines.launch

@Composable
fun CopyToClipboardButton(data: String) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current

    IconButton(
        onClick = {
            val clipData =
                ClipData.newPlainText(data, data)
                    .apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                            description.extras = PersistableBundle().apply {
                                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                            }
                    }
            scope.launch {
                data
                clipboard.setClipEntry(clipData.toClipEntry())
            }
        },
    ) {
        Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = stringResource(R.string.copy_to_clipboard_content_description)
        )
    }
}