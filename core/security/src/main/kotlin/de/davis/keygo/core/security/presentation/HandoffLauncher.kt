package de.davis.keygo.core.security.presentation

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import de.davis.keygo.core.security.domain.SystemHandoff
import de.davis.keygo.core.security.domain.forRoundTrip
import org.koin.compose.koinInject

class HandoffLauncher<I>(
    private val handoff: SystemHandoff,
    private val onLaunch: (I) -> Unit,
) {

    fun launch(input: I) = handoff.forRoundTrip { onLaunch(input) }
}

@Composable
fun <I, O> rememberHandoffLauncher(
    contract: ActivityResultContract<I, O>,
    onResult: (O) -> Unit,
): HandoffLauncher<I> {
    val handoff = koinInject<SystemHandoff>()
    val launcher = rememberLauncherForActivityResult(contract) {
        handoff.returned()
        onResult(it)
    }
    return remember(handoff, launcher) { HandoffLauncher(handoff) { launcher.launch(it) } }
}

@Composable
fun rememberHandoffStarter(): HandoffLauncher<Intent> {
    val handoff = koinInject<SystemHandoff>()
    val context = LocalContext.current
    return remember(handoff, context) { HandoffLauncher(handoff) { context.startActivity(it) } }
}
