package de.davis.keygo.feature.password_health.domain

import de.davis.keygo.feature.password_health.domain.model.KeyGoNotification

interface Notifier {

    fun canNotify(): Boolean
    fun sendNotification(notification: KeyGoNotification)
    fun cancel(kind: KeyGoNotification.Kind)
}
