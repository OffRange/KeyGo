package de.davis.keygo.feature.password_health.domain

interface HealthCheckNotifierScheduler {

    fun scheduleHealthReminder()
    fun cancel()
}
