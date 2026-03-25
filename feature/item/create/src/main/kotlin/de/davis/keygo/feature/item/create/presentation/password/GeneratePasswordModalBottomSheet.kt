package de.davis.keygo.feature.item.create.presentation.password

import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratePasswordModalBottomSheet(
    onGenerated: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(
            // Workaround for a Compose M3 bug: prevents the app from crashing
            // when the keyboard opens and shrinks the available screen height.
            // Setting this to true prevents the AnchoredDraggableUninitializedException.
            skipPartiallyExpanded = true
        ),
    ) {
        GeneratePasswordContent(
            onGenerated = onGenerated,
            containerColor = BottomSheetDefaults.ContainerColor,
        )
    }
}