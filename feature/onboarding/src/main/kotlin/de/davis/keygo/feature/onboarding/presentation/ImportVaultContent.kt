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
import de.davis.keygo.feature.backup.presentation.component.BackupFileChooserCard
import de.davis.keygo.feature.onboarding.R
import de.davis.keygo.feature.onboarding.presentation.component.OnboardingScaffold
import de.davis.keygo.feature.onboarding.presentation.component.SmallIconContainer
import de.davis.keygo.feature.backup.R as BackupR

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ImportVaultContent(onChooseFile: () -> Unit) {
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
    ) {
        BackupFileChooserCard(
            icon = Icons.Default.FileOpen,
            title = stringResource(BackupR.string.import_choose_title),
            subtitle = stringResource(BackupR.string.import_choose_subtitle),
            action = stringResource(BackupR.string.import_choose_action),
            onChoose = onChooseFile,
        )
    }
}

@Preview
@Composable
private fun ImportVaultContentPreview() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            ImportVaultContent(onChooseFile = {})
        }
    }
}
