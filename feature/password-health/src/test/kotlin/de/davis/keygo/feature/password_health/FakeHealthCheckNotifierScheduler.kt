package de.davis.keygo.feature.password_health

import de.davis.keygo.feature.password_health.domain.HealthCheckNotifierScheduler

internal class FakeHealthCheckNotifierScheduler : HealthCheckNotifierScheduler {

    var scheduled = false
        private set

    override fun scheduleHealthReminder() {
        scheduled = true
    }

    override fun cancel() {
        scheduled = false
    }
}
