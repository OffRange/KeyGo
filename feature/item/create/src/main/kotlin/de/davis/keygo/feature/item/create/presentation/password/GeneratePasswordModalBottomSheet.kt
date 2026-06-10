package de.davis.keygo.feature.item.create.presentation.password

import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratePasswordModalBottomSheet(
    onGenerated: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
        ),
    ) {
        GeneratePasswordContent(
            onGenerated = onGenerated,
            containerColor = BottomSheetDefaults.ContainerColor,
        )
    }
}