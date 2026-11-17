package de.davis.keygo.feature.account.presentation.components

import kotlin.test.Test
import kotlin.test.assertEquals

class AccountInitialsTest {

    @Test
    fun `single word username yields one letter`() {
        assertEquals("A", accountInitials("ada"))
    }

    @Test
    fun `first and last word make the initials`() {
        assertEquals("AL", accountInitials("ada.lovelace"))
        assertEquals("AL", accountInitials("ada-lovelace"))
        assertEquals("AL", accountInitials("ada_lovelace"))
    }

    @Test
    fun `middle words are skipped`() {
        assertEquals("AL", accountInitials("ada.king.lovelace"))
    }

    @Test
    fun `repeated and edge separators are ignored`() {
        assertEquals("AL", accountInitials("_ada__lovelace-"))
    }

    @Test
    fun `digits count as letters`() {
        assertEquals("A2", accountInitials("ada.2"))
    }

    @Test
    fun `username without letters yields nothing`() {
        assertEquals("", accountInitials("._-"))
    }
}
