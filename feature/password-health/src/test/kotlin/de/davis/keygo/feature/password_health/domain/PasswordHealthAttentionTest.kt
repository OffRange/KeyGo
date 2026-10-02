package de.davis.keygo.feature.password_health.domain

import de.davis.keygo.core.security.FakeSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PasswordHealthAttentionTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @AfterTest
    fun tearDown() = scope.cancel()

    @Test
    fun anUnlockedSessionShowsTheCount() {
        val attention = PasswordHealthAttention(FakeSession(startUnlocked = true), scope)

        attention.update(3)

        assertEquals(3, attention.needsAttention.value)
    }

    @Test
    fun aCountPublishedWhileLockedStaysHidden() {
        val attention = PasswordHealthAttention(FakeSession(), scope)

        attention.update(3)

        assertEquals(0, attention.needsAttention.value)
    }
}
