package de.davis.keygo.feature.onboarding.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import de.davis.keygo.feature.backup.presentation.component.BackupFileChooser
import de.davis.keygo.feature.onboarding.R
import de.davis.keygo.feature.onboarding.presentation.component.OnboardingScaffold
import de.davis.keygo.feature.onboarding.presentation.component.SmallIconContainer
import de.davis.keygo.feature.backup.R as BackupR

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ImportVaultContent(
    onContinue: () -> Unit
) {
    OnboardingScaffold(
        iconContainer = {
            SmallIconContainer(
                shape = MaterialShapes.Pentagon.toShape()
            ) {
                Icon(
                    imageVector = Icons.Default.ImportExport,
                    contentDescription = null
                )
            }
        },
        title = stringResource(R.string.import_title),
        description = stringResource(R.string.import_subtitle),
        buttonText = stringResource(R.string.skip_for_now),
        buttonOutlined = true,
        onButtonClicked = onContinue,
    ) {
        BackupFileChooser(
            destination = null,
            onChoose = {},
            chooserIcon = Icons.Default.FileOpen,
            chooserTitle = stringResource(BackupR.string.destination_choose_title),
            chooserSubtitle = stringResource(BackupR.string.destination_choose_subtitle),
            chooserAction = stringResource(BackupR.string.destination_choose_action),
            changeLabel = stringResource(BackupR.string.destination_filename_label),
            fileNameLabel = "TODO", // TODO
        )
    }
}

@Preview
@Composable
private fun ImportVaultContentPreview() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            ImportVaultContent(
                onContinue = {}
            )
        }
    }
}