package de.davis.keygo.feature.password_health

import de.davis.keygo.feature.password_health.domain.Notifier
import de.davis.keygo.feature.password_health.domain.model.KeyGoNotification

internal class FakeNotifier(var canNotify: Boolean = true) : Notifier {

    val sent = mutableListOf<KeyGoNotification>()
    val cancelled = mutableListOf<KeyGoNotification.Kind>()

    override fun canNotify(): Boolean = canNotify

    override fun sendNotification(notification: KeyGoNotification) {
        sent += notification
    }

    override fun cancel(kind: KeyGoNotification.Kind) {
        cancelled += kind
    }
}
