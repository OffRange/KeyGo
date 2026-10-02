package de.davis.keygo.feature.password_health.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class VaultFingerprintTest {

    private val a = HealthFingerprint(byteArrayOf(1))
    private val b = HealthFingerprint(byteArrayOf(-1))
    private val c = HealthFingerprint(byteArrayOf(2))

    @Test
    fun orderDoesNotMatter() {
        assertEquals(VaultFingerprint.of(listOf(a, b, c)), VaultFingerprint.of(listOf(c, a, b)))
    }

    @Test
    fun aChangedPasswordChangesIt() {
        assertNotEquals(VaultFingerprint.of(listOf(a, b)), VaultFingerprint.of(listOf(a, c)))
    }

    @Test
    fun anAddedPasswordChangesIt() {
        assertNotEquals(VaultFingerprint.of(listOf(a, b)), VaultFingerprint.of(listOf(a, b, c)))
    }
}
