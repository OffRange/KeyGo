package de.davis.keygo.feature.backup.presentation.contract

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.annotation.CallSuper
import de.davis.keygo.feature.backup.presentation.export.model.ExportWizardEvent

internal class CreateDynamicDocument :
    ActivityResultContract<ExportWizardEvent.CreateFile, Uri?>() {

    @CallSuper
    override fun createIntent(context: Context, input: ExportWizardEvent.CreateFile): Intent {
        return Intent(Intent.ACTION_CREATE_DOCUMENT)
            .setType(input.mimeType)
            .putExtra(Intent.EXTRA_TITLE, input.suggestedName)
    }

    override fun getSynchronousResult(
        context: Context,
        input: ExportWizardEvent.CreateFile,
    ): SynchronousResult<Uri?>? = null

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return intent.takeIf { resultCode == Activity.RESULT_OK }?.data
    }
}