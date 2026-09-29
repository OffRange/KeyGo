package de.davis.keygo.feature.list_screen.presentation.mapper

import de.davis.keygo.core.item.domain.model.CredentialType
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.domain.model.Tag
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.feature.list_screen.domain.model.FacetSelections
import de.davis.keygo.feature.list_screen.domain.model.FilterFacet
import de.davis.keygo.feature.list_screen.domain.model.FilterState
import de.davis.keygo.feature.list_screen.presentation.model.FilterBottomSheetState
import de.davis.keygo.feature.list_screen.presentation.model.FilterChipState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FilterMapperTest {

    private fun tag(value: String): Tag = Tag.of(value)!!

    // Every sheetFor() call defaults to one available Login item, matching the shape
    // AvailableFacetValuesUseCase would produce for a single, unpinned, untagged login.
    private val defaultFacets =
        FacetSelections.None.with(FilterFacet.ItemTypes, setOf(VaultItemType.Login))

    private fun sheetFor(
        filter: FilterState,
        facets: FacetSelections = defaultFacets,
        retained: FacetSelections = FacetSelections.None,
    ) = filter.toBottomSheetState(
        available = facets.toAvailableFilterOptions(allTags = emptyList()),
        restrictedItemType = null,
        retained = retained,
        isVisible = false,
    )

    private fun scoresAvailable(vararg scores: PasswordScore): FacetSelections =
        defaultFacets.with(FilterFacet.PasswordScores, scores.toSet())

    private fun FilterBottomSheetState.scoreChips() =
        loginSection?.passwordScoreChips.orEmpty().map { it.value to it.selected }

    private fun FilterBottomSheetState.credentialChips() =
        loginSection?.credentialChips.orEmpty().map { it.value to it.selected }

    @Test
    fun `tags are ordered by allTags and limited to the values present in the selections`() {
        val available = FacetSelections.None
            .with(FilterFacet.Tags, setOf(tag("Bank"), tag("Work")))
            // "Personal" is not among the selections' tags -> must be excluded.
            .toAvailableFilterOptions(allTags = listOf(tag("Bank"), tag("Personal"), tag("Work")))

        assertEquals(listOf(tag("Bank"), tag("Work")), available.tags.available.toList())
    }

    @Test
    fun `selected tags are reflected as selected chips`() {
        val available = FacetSelections.None
            .with(FilterFacet.Tags, setOf(tag("Bank"), tag("Work")))
            .toAvailableFilterOptions(allTags = listOf(tag("Bank"), tag("Work")))

        val sheet = FilterState().with(FilterFacet.Tags, setOf(tag("Bank")))
            .toBottomSheetState(
                available,
                restrictedItemType = null,
                retained = FacetSelections.None,
                isVisible = false,
            )

        val chips = sheet.itemSection?.tagChips.orEmpty()
        assertEquals(setOf(tag("Bank"), tag("Work")), chips.map { it.value }.toSet())
        assertTrue(chips.single { it.value == tag("Bank") }.selected)
        assertTrue(!chips.single { it.value == tag("Work") }.selected)
    }

    @Test
    fun `a selected score no item carries any more keeps its chip, still selected`() {
        val sheet = sheetFor(
            FilterState().with(FilterFacet.PasswordScores, setOf(PasswordScore.Weak)),
            scoresAvailable(PasswordScore.Strong),
        )

        assertEquals(
            listOf(PasswordScore.Strong to false, PasswordScore.Weak to true),
            sheet.scoreChips(),
        )
    }

    @Test
    fun `a retained score keeps its chip unselected, in display order`() {
        val sheet = sheetFor(
            FilterState(),
            scoresAvailable(PasswordScore.Excellent, PasswordScore.Ridiculous),
            retained = FacetSelections.None.with(
                FilterFacet.PasswordScores,
                setOf(PasswordScore.Weak)
            ),
        )

        assertEquals(
            listOf(
                PasswordScore.Excellent to false,
                PasswordScore.Weak to false,
                PasswordScore.Ridiculous to false,
            ),
            sheet.scoreChips(),
        )
    }

    @Test
    fun `a deselected score no item carries loses its chip once nothing retains it`() {
        val sheet = sheetFor(FilterState(), scoresAvailable(PasswordScore.Strong))

        assertEquals(listOf(PasswordScore.Strong to false), sheet.scoreChips())
    }

    @Test
    fun `the login section stays for a selected score after the last login is gone`() {
        val sheet = sheetFor(
            FilterState().with(FilterFacet.PasswordScores, setOf(PasswordScore.Weak)),
            facets = FacetSelections.None.with(
                FilterFacet.ItemTypes,
                setOf(VaultItemType.CreditCard)
            ),
        )

        assertEquals(listOf(PasswordScore.Weak to true), sheet.scoreChips())
    }

    @Test
    fun `the pinned switch stays while only pinned is on and nothing is pinned`() {
        val sheet = sheetFor(FilterState().with(FilterFacet.Pinned, setOf(true)))

        assertEquals(FilterChipState(value = true, selected = true), sheet.itemSection?.onlyPinned)
    }

    @Test
    fun `no pinned switch when nothing is pinned and the filter is off`() {
        assertNull(sheetFor(FilterState()).itemSection?.onlyPinned)
    }

    @Test
    fun `a single item type offers no chips`() {
        assertTrue(sheetFor(FilterState()).itemSection?.itemTypeChips.orEmpty().isEmpty())
    }

    @Test
    fun `item type chips stay while the only type left is selected`() {
        val sheet = sheetFor(FilterState().with(FilterFacet.ItemTypes, setOf(VaultItemType.Login)))

        assertEquals(
            listOf(FilterChipState(value = VaultItemType.Login, selected = true)),
            sheet.itemSection?.itemTypeChips,
        )
    }

    @Test
    fun `item type chips stay while the sheet retains the only type left`() {
        val sheet = sheetFor(
            FilterState(),
            retained = FacetSelections.None.with(FilterFacet.ItemTypes, setOf(VaultItemType.Login)),
        )

        assertEquals(
            listOf(FilterChipState(value = VaultItemType.Login, selected = false)),
            sheet.itemSection?.itemTypeChips,
        )
    }

    @Test
    fun `a selected tag no item carries any more is listed after the live tags`() {
        val available = FacetSelections.None
            .with(FilterFacet.Tags, setOf(tag("Work")))
            .toAvailableFilterOptions(allTags = listOf(tag("Work")))

        val sheet = FilterState().with(FilterFacet.Tags, setOf(tag("Bank")))
            .toBottomSheetState(
                available,
                restrictedItemType = null,
                retained = FacetSelections.None,
                isVisible = false,
            )

        assertEquals(
            listOf(
                FilterChipState(value = tag("Work"), selected = false),
                FilterChipState(value = tag("Bank"), selected = true),
            ),
            sheet.itemSection?.tagChips,
        )
    }

    @Test
    fun `credential chips follow the credential order, whatever order they arrive in`() {
        val sheet = sheetFor(
            FilterState().with(FilterFacet.Credentials, setOf(CredentialType.Totp)),
            facets = defaultFacets.with(
                FilterFacet.Credentials,
                setOf(CredentialType.Totp, CredentialType.Password),
            ),
        )

        assertEquals(
            listOf(CredentialType.Password to false, CredentialType.Totp to true),
            sheet.credentialChips(),
        )
    }

    @Test
    fun `the login section shows for credentials alone, without score chips`() {
        val sheet = sheetFor(
            FilterState(),
            facets = defaultFacets.with(FilterFacet.Credentials, setOf(CredentialType.Passkey)),
        )

        assertEquals(emptyList(), sheet.scoreChips())
        assertEquals(listOf(CredentialType.Passkey to false), sheet.credentialChips())
    }

    @Test
    fun `no login section while the selected item types exclude logins`() {
        val sheet = sheetFor(
            FilterState().with(FilterFacet.ItemTypes, setOf(VaultItemType.CreditCard)),
            facets = defaultFacets
                .with(FilterFacet.ItemTypes, setOf(VaultItemType.Login, VaultItemType.CreditCard))
                .with(FilterFacet.Credentials, setOf(CredentialType.Passkey))
                .with(FilterFacet.PasswordScores, setOf(PasswordScore.Strong)),
        )

        assertNull(sheet.loginSection)
    }
}
