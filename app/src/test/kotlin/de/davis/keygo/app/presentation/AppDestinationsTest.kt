package de.davis.keygo.app.presentation

import de.davis.keygo.feature.password_health.presentation.ACTION_OPEN_PASSWORD_HEALTH
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppDestinationsTest {

    @Test
    fun `the password health action opens the password health tab`() {
        assertEquals(
            AppDestinations.PASSWORD_HEALTH,
            AppDestinations.fromIntentAction(ACTION_OPEN_PASSWORD_HEALTH),
        )
    }

    @Test
    fun `the launcher action opens no tab`() {
        assertNull(AppDestinations.fromIntentAction("android.intent.action.MAIN"))
    }

    @Test
    fun `no action opens no tab, even though most tabs have none`() {
        assertNull(AppDestinations.fromIntentAction(null))
    }
}
