package de.davis.keygo.feature.list_screen.domain.model

import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilterStateTest {

    @Test
    fun `default filter state is default`() {
        assertTrue(FilterState().isDefault)
    }

    @Test
    fun `ascending sort with no scores is default`() {
        val state = FilterState(
            sortDirection = SortDirection.Ascending,
            selectedScores = emptySet(),
        )
        assertTrue(state.isDefault)
    }

    @Test
    fun `descending sort is not default`() {
        val state = FilterState(
            sortDirection = SortDirection.Descending,
        )
        assertFalse(state.isDefault)
    }

    @Test
    fun `non-empty selected scores is not default`() {
        val state = FilterState(
            selectedScores = setOf(PasswordScore.Weak),
        )
        assertFalse(state.isDefault)
    }

    @Test
    fun `both non-default sort and scores is not default`() {
        val state = FilterState(
            sortDirection = SortDirection.Descending,
            selectedScores = setOf(PasswordScore.Strong, PasswordScore.Excellent),
        )
        assertFalse(state.isDefault)
    }

    @Test
    fun `non-empty selected item types is not default`() {
        val state = FilterState(
            selectedItemTypes = setOf(VaultItemType.Login),
        )
        assertFalse(state.isDefault)
    }

    @Test
    fun `non-empty selected tags is not default`() {
        val state = FilterState(
            selectedTags = setOf("work"),
        )
        assertFalse(state.isDefault)
    }

    @Test
    fun `all fields non-default is not default`() {
        val state = FilterState(
            sortDirection = SortDirection.Descending,
            selectedScores = setOf(PasswordScore.Weak),
            selectedItemTypes = setOf(VaultItemType.Login),
            selectedTags = setOf("personal"),
        )
        assertFalse(state.isDefault)
    }
}