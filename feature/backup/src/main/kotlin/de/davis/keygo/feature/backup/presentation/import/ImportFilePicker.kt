package de.davis.keygo.feature.backup.presentation.import

import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import de.davis.keygo.core.security.presentation.rememberHandoffLauncher
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri
import de.davis.keygo.feature.backup.domain.model.FileFormat

private val ImportFileMimeTypes = (FileFormat.entries.map { it.mimeType } + "*/*").toTypedArray()

fun interface FilePickerAction {
    fun launch()
}

@Composable
fun rememberImportFilePicker(onPicked: (BackupDestinationUri) -> Unit): FilePickerAction {
    val launcher = rememberHandoffLauncher(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { onPicked(BackupDestinationUri(it.toString())) }
    }
    return remember(launcher) { FilePickerAction { launcher.launch(ImportFileMimeTypes) } }
}
