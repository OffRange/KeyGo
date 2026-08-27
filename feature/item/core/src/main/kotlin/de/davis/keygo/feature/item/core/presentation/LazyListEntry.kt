package de.davis.keygo.feature.item.core.presentation

import android.content.ClipData
import android.content.ClipDescription
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.toClipEntry
import de.davis.keygo.core.ui.components.KeyGoCard
import de.davis.keygo.feature.item.core.R
import kotlinx.coroutines.launch

fun LazyListScope.entry(
    title: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    item(key = title) {
        KeyGoCard(
            title = {
                Text(text = title)
            },
            leadingItem = {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                )
            },
            trailingItem = trailingContent,
            modifier = modifier.animateItem(),
        ) {
            content()
        }
    }
}

fun LazyListScope.copyableEntry(
    title: String,
    leadingIcon: ImageVector,
    dataToCopy: () -> String,
    sensitive: Boolean = false,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    item(key = title) {
        val scope = rememberCoroutineScope()
        val clipboard = LocalClipboard.current
        val context = LocalContext.current
        val resources = LocalResources.current

        KeyGoCard(
            title = {
                Text(text = title)
            },
            leadingItem = {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                )
            },
            trailingItem = trailingContent,
            modifier = modifier
                .animateItem()
                .clickable {
                    val data = dataToCopy()
                    val clipData = ClipData.newPlainText(data, data).apply {
                        if (sensitive) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                                description.extras = PersistableBundle().apply {
                                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                                }
                        }
                    }
                    scope.launch {
                        clipboard.setClipEntry(clipData.toClipEntry())

                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
                            Toast.makeText(
                                context,
                                resources.getString(R.string.copied, title),
                                Toast.LENGTH_SHORT
                            ).show()
                    }
                },
        ) {
            content()
        }
    }
}
