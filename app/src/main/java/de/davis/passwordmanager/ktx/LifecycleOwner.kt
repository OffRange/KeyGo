package de.davis.passwordmanager.ktx

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

fun <E, R> LifecycleOwner.doFlowInLifecycle(
    flow: Flow<E>,
    state: Lifecycle.State = Lifecycle.State.STARTED,
    operationBlock: suspend Flow<E>.() -> R
) {
    lifecycleScope.launch {
        repeatOnLifecycle(state) {
            operationBlock(flow)
        }
    }
}