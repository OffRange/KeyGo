package de.davis.keygo.feature.list_screen.domain.model

import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FacetSelectionsTest {

    @Test
    fun `a facet nothing was selected in is empty`() {
        assertTrue(FacetSelections.None[FilterFacet.Tags].isEmpty())
    }

    @Test
    fun `toggling a value on and off again is the same as never touching it`() {
        val toggled = FacetSelections.None
            .toggle(FilterFacet.PasswordScores, PasswordScore.Weak)
            .toggle(FilterFacet.PasswordScores, PasswordScore.Weak)

        assertEquals(FacetSelections.None, toggled)
        assertTrue(toggled.isEmpty)
    }

    @Test
    fun `toggling one facet leaves the others alone`() {
        val selections = FacetSelections.None
            .toggle(FilterFacet.ItemTypes, VaultItemType.Login)
            .toggle(FilterFacet.PasswordScores, PasswordScore.Weak)

        assertEquals(setOf(VaultItemType.Login), selections[FilterFacet.ItemTypes])
        assertEquals(setOf(PasswordScore.Weak), selections[FilterFacet.PasswordScores])
    }

    @Test
    fun `plus unions the values of every facet`() {
        val left = FacetSelections.None
            .with(FilterFacet.PasswordScores, setOf(PasswordScore.Weak))
            .with(FilterFacet.Pinned, setOf(true))
        val right = FacetSelections.None
            .with(FilterFacet.PasswordScores, setOf(PasswordScore.Strong))
            .with(FilterFacet.ItemTypes, setOf(VaultItemType.Login))

        val union = left + right

        assertEquals(
            setOf(PasswordScore.Weak, PasswordScore.Strong),
            union[FilterFacet.PasswordScores],
        )
        assertEquals(setOf(true), union[FilterFacet.Pinned])
        assertEquals(setOf(VaultItemType.Login), union[FilterFacet.ItemTypes])
    }
}
