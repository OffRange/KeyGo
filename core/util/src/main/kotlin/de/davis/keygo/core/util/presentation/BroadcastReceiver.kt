package de.davis.keygo.core.util.presentation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
fun BroadcastReceiver(
    action: String,
    flags: Int = ContextCompat.RECEIVER_NOT_EXPORTED,
    onReceive: (Intent?) -> Unit
) {
    val context = LocalContext.current
    val currentOnReceive by rememberUpdatedState(onReceive)

    DisposableEffect(context, action) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                currentOnReceive(intent)
            }
        }

        val filter = IntentFilter(action)
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            flags,
        )

        onDispose { context.unregisterReceiver(receiver) }
    }
}