package de.davis.keygo.feature.item.view

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput

fun Modifier.onHold(onHold: (Boolean) -> Unit) = this.pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown()
        onHold(true)

        try {
            val pointerId = down.id
            do {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == pointerId }
                if (change == null || change.changedToUpIgnoreConsumed()) {
                    break
                }

                change.consume()
            } while (true)
        } finally {
            onHold(false)
        }
    }
}