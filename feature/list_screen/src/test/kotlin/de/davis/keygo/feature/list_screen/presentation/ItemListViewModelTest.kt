package de.davis.keygo.feature.list_screen.presentation

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import de.davis.keygo.core.item.FakeCreditCardRepository
import de.davis.keygo.core.item.FakeItemRepository
import de.davis.keygo.core.item.FakeLoginRepository
import de.davis.keygo.core.item.FakeVaultContextRepository
import de.davis.keygo.core.item.FakeVaultRepository
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.CardExpiryStatus
import de.davis.keygo.core.item.domain.model.CreditCard
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.model.Tag
import de.davis.keygo.core.item.domain.model.Timestamp
import de.davis.keygo.core.item.domain.usecase.ObserveAllTagsSortedUseCase
import de.davis.keygo.core.util.domain.usecase.SortUseCase
import de.davis.keygo.feature.list_screen.domain.model.FilterFacet
import de.davis.keygo.feature.list_screen.domain.usecase.AvailableFacetValuesUseCase
import de.davis.keygo.feature.list_screen.domain.usecase.FilterUseCase
import de.davis.keygo.feature.list_screen.domain.usecase.ObserveCardExpiryStatusesUseCase
import de.davis.keygo.feature.list_screen.domain.usecase.ObserveFilterResultUseCase
import de.davis.keygo.feature.list_screen.domain.usecase.RankSearchResultsUseCase
import de.davis.keygo.feature.list_screen.presentation.model.Event
import de.davis.keygo.feature.list_screen.presentation.model.FilterAction
import de.davis.keygo.feature.list_screen.presentation.model.FilterChipState
import de.davis.keygo.feature.vault.domain.usecase.ObserveVaultsAndSelectionUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.time.YearMonth
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Deletion is committed while the user is present, so these assert against what the repository
 * actually holds afterwards rather than against anything still pending in the ViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ItemListViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val vaultId = newVaultId()
    private val loginRepository = FakeLoginRepository()
    private val itemRepository = FakeItemRepository(loginRepository)
    private val vaultRepository = FakeVaultRepository()
    private val vaultContextRepository = FakeVaultContextRepository()
    private val creditCardRepository = FakeCreditCardRepository()
    private val sortUseCase = SortUseCase()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(enableSelection: Boolean = true) = ItemListViewModel(
        enableSelection = enableSelection,
        restrictedItemType = null,
        itemRepository = itemRepository,
        rankSearchResults = RankSearchResultsUseCase(sortUseCase),
        observeAllTags = ObserveAllTagsSortedUseCase(itemRepository, sortUseCase),
        observeVaultsAndSelection = ObserveVaultsAndSelectionUseCase(
            vaultRepository = vaultRepository,
            vaultContextRepository = vaultContextRepository,
            sortUseCase = sortUseCase,
        ),
        observeFilterResult = ObserveFilterResultUseCase(
            itemRepository = itemRepository,
            loginRepository = loginRepository,
            filterUseCase = FilterUseCase(sortUseCase),
            availableFacetValues = AvailableFacetValuesUseCase(),
        ),
        observeCardExpiryStatuses = ObserveCardExpiryStatusesUseCase(creditCardRepository),
    )

    private fun login(
        name: String,
        id: ItemId = newItemId(),
        vault: VaultId = vaultId,
        pinned: Boolean = false,
        tags: Set<Tag> = emptySet(),
    ) = Login(
        id = id,
        name = name,
        username = null,
        domainInfos = emptySet(),
        passwordCredential = null,
        totp = null,
        passkeys = emptySet(),
        note = null,
        pinned = pinned,
        vaultId = vault,
        keyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
        timestamp = Timestamp(),
        tags = tags,
    )

    private suspend fun storedIds(): Set<ItemId> =
        itemRepository.observeLiteVaultItems().first().mapTo(mutableSetOf()) { it.id }

    private suspend fun pinnedIds(): Set<ItemId> =
        itemRepository.observeLiteVaultItems().first()
            .filter { it.pinned }
            .mapTo(mutableSetOf()) { it.id }

    /**
     * The bug this flow replaced: each swipe hung its delete off a snackbar that the next swipe
     * replaced, so every item after the first was hidden but never actually deleted.
     */
    @Test
    fun `deleting a whole selection removes every item in it`() = runTest(dispatcher) {
        val first = login("First")
        val second = login("Second")
        val third = login("Third")
        loginRepository.seed(first, second, third)

        val vm = viewModel()
        vm.onItemLongClick(first.id)
        vm.onItemClick(second.id)
        vm.onItemClick(third.id)

        vm.onDeleteSelectedRequest()
        vm.onConfirmDeleteSelected()
        advanceUntilIdle()

        assertEquals(emptySet(), storedIds())
    }

    @Test
    fun `deleting a selection leaves the items outside it alone`() = runTest(dispatcher) {
        val doomed = login("Doomed")
        val survivor = login("Survivor")
        loginRepository.seed(doomed, survivor)

        val vm = viewModel()
        vm.onItemLongClick(doomed.id)
        vm.onDeleteSelectedRequest()
        vm.onConfirmDeleteSelected()
        advanceUntilIdle()

        assertEquals(setOf(survivor.id), storedIds())
    }

    @Test
    fun `dismissing the confirmation deletes nothing and keeps the selection`() =
        runTest(dispatcher) {
            val item = login("Kept")
            loginRepository.seed(item)

            val vm = viewModel()
            backgroundScope.launchCollect(vm)
            advanceUntilIdle()

            vm.onItemLongClick(item.id)
            vm.onDeleteSelectedRequest()
            vm.onDismissDeleteConfirmation()
            advanceUntilIdle()

            assertEquals(setOf(item.id), storedIds())
            assertEquals(setOf(item.id), vm.listItemState.value.selectedItemIds)
            assertFalse(vm.listItemState.value.isDeleteConfirmationVisible)
        }

    @Test
    fun `confirming clears the selection so a second confirm deletes nothing more`() =
        runTest(dispatcher) {
            val doomed = login("Doomed")
            val survivor = login("Survivor")
            loginRepository.seed(doomed, survivor)

            val vm = viewModel()
            backgroundScope.launchCollect(vm)
            advanceUntilIdle()

            vm.onItemLongClick(doomed.id)
            vm.onDeleteSelectedRequest()
            vm.onConfirmDeleteSelected()
            advanceUntilIdle()

            vm.onConfirmDeleteSelected()
            advanceUntilIdle()

            assertEquals(setOf(survivor.id), storedIds())
            assertEquals(emptySet(), vm.listItemState.value.selectedItemIds)
        }

    @Test
    fun `requesting a delete with nothing selected does not open the dialog`() =
        runTest(dispatcher) {
            loginRepository.seed(login("Untouched"))

            val vm = viewModel()
            backgroundScope.launchCollect(vm)
            advanceUntilIdle()

            vm.onDeleteSelectedRequest()
            advanceUntilIdle()

            assertFalse(vm.listItemState.value.isDeleteConfirmationVisible)
        }

    @Test
    fun `select all selects every listed item`() = runTest(dispatcher) {
        val items = listOf(login("A"), login("B"), login("C"))
        loginRepository.seed(*items.toTypedArray())

        val vm = viewModel()
        backgroundScope.launchCollect(vm)
        advanceUntilIdle()

        vm.onSelectAll()
        advanceUntilIdle()

        assertEquals(items.mapTo(mutableSetOf()) { it.id }, vm.listItemState.value.selectedItemIds)
        assertTrue(vm.listItemState.value.isSelectionActive)
    }

    @Test
    fun `select all does nothing on a screen that does not allow selection`() =
        runTest(dispatcher) {
            loginRepository.seed(login("A"))

            val vm = viewModel(enableSelection = false)
            backgroundScope.launchCollect(vm)
            advanceUntilIdle()

            vm.onSelectAll()
            advanceUntilIdle()

            assertEquals(emptySet(), vm.listItemState.value.selectedItemIds)
        }

    /**
     * The search branch feeds off a live query, so the delete has to reach the results on its own.
     * Pins that: a snapshot source would leave the deleted rows on screen until the query changed.
     */
    @Test
    fun `deleting while a search is submitted drops the rows from the results`() =
        runTest(dispatcher) {
            val doomed = login("Doomed")
            val survivor = login("Survivor")
            loginRepository.seed(doomed, survivor)

            val vm = viewModel()
            backgroundScope.launchCollect(vm)
            vm.searchTextFieldState.setTextAndPlaceCursorAtEnd("o")
            vm.onSubmitQuery()
            advanceUntilIdle()

            assertEquals(
                setOf(doomed.id, survivor.id),
                vm.listItemState.value.items.mapTo(mutableSetOf()) { it.id },
            )

            vm.onItemLongClick(doomed.id)
            vm.onDeleteSelectedRequest()
            vm.onConfirmDeleteSelected()
            advanceUntilIdle()

            assertEquals(
                setOf(survivor.id),
                vm.listItemState.value.items.mapTo(mutableSetOf()) { it.id },
            )
        }

    @Test
    fun `clearing the selection leaves every item in place`() = runTest(dispatcher) {
        val item = login("Kept")
        loginRepository.seed(item)

        val vm = viewModel()
        backgroundScope.launchCollect(vm)
        advanceUntilIdle()

        vm.onItemLongClick(item.id)
        vm.onClearSelection()
        advanceUntilIdle()

        assertEquals(setOf(item.id), storedIds())
        assertEquals(emptySet(), vm.listItemState.value.selectedItemIds)
    }

    @Test
    fun `pinning a selection pins every item in it`() = runTest(dispatcher) {
        val first = login("First")
        val second = login("Second")
        loginRepository.seed(first, second)

        val vm = viewModel()
        backgroundScope.launchCollect(vm)
        advanceUntilIdle()

        vm.onItemLongClick(first.id)
        vm.onItemClick(second.id)
        vm.onPinSelectedRequest()
        advanceUntilIdle()

        assertEquals(setOf(first.id, second.id), pinnedIds())
        assertTrue(vm.listItemState.value.allSelectedPinned)
    }

    /** The mixed case: one unpinned item is enough to make the button pin rather than unpin. */
    @Test
    fun `a selection holding one unpinned item pins the whole selection`() = runTest(dispatcher) {
        val alreadyPinned = login("Pinned", pinned = true)
        val plain = login("Plain")
        loginRepository.seed(alreadyPinned, plain)

        val vm = viewModel()
        backgroundScope.launchCollect(vm)
        advanceUntilIdle()

        vm.onItemLongClick(alreadyPinned.id)
        vm.onItemClick(plain.id)
        advanceUntilIdle()
        assertFalse(vm.listItemState.value.allSelectedPinned)

        vm.onPinSelectedRequest()
        advanceUntilIdle()

        assertEquals(setOf(alreadyPinned.id, plain.id), pinnedIds())
    }

    @Test
    fun `pinning a selection whose items are all pinned unpins them`() = runTest(dispatcher) {
        val first = login("First", pinned = true)
        val second = login("Second", pinned = true)
        loginRepository.seed(first, second)

        val vm = viewModel()
        backgroundScope.launchCollect(vm)
        advanceUntilIdle()

        vm.onItemLongClick(first.id)
        vm.onItemClick(second.id)
        advanceUntilIdle()
        assertTrue(vm.listItemState.value.allSelectedPinned)

        vm.onPinSelectedRequest()
        advanceUntilIdle()

        assertEquals(emptySet(), pinnedIds())
        assertFalse(vm.listItemState.value.allSelectedPinned)
    }

    /**
     * The selection carries a pinned flag per item, so dropping the one unpinned item has to hand
     * the action back to unpin. A single "are they all pinned" boolean could not recover this.
     */
    @Test
    fun `deselecting the only unpinned item turns the action back into an unpin`() =
        runTest(dispatcher) {
            val alreadyPinned = login("Pinned", pinned = true)
            val plain = login("Plain")
            loginRepository.seed(alreadyPinned, plain)

            val vm = viewModel()
            backgroundScope.launchCollect(vm)
            advanceUntilIdle()

            vm.onItemLongClick(alreadyPinned.id)
            vm.onItemClick(plain.id)
            advanceUntilIdle()
            assertFalse(vm.listItemState.value.allSelectedPinned)

            vm.onItemClick(plain.id)
            advanceUntilIdle()

            assertEquals(setOf(alreadyPinned.id), vm.listItemState.value.selectedItemIds)
            assertTrue(vm.listItemState.value.allSelectedPinned)
        }

    @Test
    fun `select all carries the pinned state of every item it picks up`() = runTest(dispatcher) {
        loginRepository.seed(login("A", pinned = true), login("B", pinned = true))

        val vm = viewModel()
        backgroundScope.launchCollect(vm)
        advanceUntilIdle()

        vm.onSelectAll()
        advanceUntilIdle()

        assertTrue(vm.listItemState.value.allSelectedPinned)
    }

    @Test
    fun `requesting a pin with nothing selected pins nothing`() = runTest(dispatcher) {
        loginRepository.seed(login("Untouched"))

        val vm = viewModel()
        backgroundScope.launchCollect(vm)
        advanceUntilIdle()

        vm.onPinSelectedRequest()
        advanceUntilIdle()

        assertEquals(emptySet(), pinnedIds())
        assertFalse(vm.listItemState.value.allSelectedPinned)
    }

    /**
     * Which row is marked as open is read off the detail pane by the screen, not kept here. All
     * this owes the caller is the event that moves the pane in the first place.
     */
    @Test
    fun `clicking an item asks for it to be shown`() = runTest(dispatcher) {
        val opened = login("Opened")
        loginRepository.seed(opened, login("Other"))

        val vm = viewModel()
        backgroundScope.launchCollect(vm)
        advanceUntilIdle()

        val selected = async { vm.event.first() }
        advanceUntilIdle()

        vm.onItemClick(opened.id)
        advanceUntilIdle()

        assertEquals(Event.ItemSelected(opened.id), selected.await())
    }

    @Test
    fun `deleting every item a filter matches leaves a list that is empty because of the filter`() =
        runTest(dispatcher) {
            val pinned = login("Pinned", pinned = true)
            loginRepository.seed(pinned, login("Loose"))

            val vm = viewModel()
            backgroundScope.launchCollect(vm)
            vm.onFilterAction(FilterAction.Toggled(FilterFacet.Pinned, true))
            vm.onItemLongClick(pinned.id)
            vm.onDeleteSelectedRequest()
            vm.onConfirmDeleteSelected()
            advanceUntilIdle()

            assertTrue(vm.listItemState.value.items.isEmpty())
            assertTrue(vm.listItemState.value.isEmptyBecauseOfFilter)
        }

    @Test
    fun `an empty vault is not empty because of the filter`() = runTest(dispatcher) {
        val vm = viewModel()
        backgroundScope.launchCollect(vm)
        vm.onFilterAction(FilterAction.Toggled(FilterFacet.Pinned, true))
        advanceUntilIdle()

        assertFalse(vm.listItemState.value.isEmptyBecauseOfFilter)
    }

    @Test
    fun `a filter nothing matches any more keeps its control until deselected and the sheet closes`() =
        runTest(dispatcher) {
            loginRepository.seed(login("Loose"))

            val vm = viewModel()
            backgroundScope.launchCollect(vm)
            vm.onFilterAction(FilterAction.Toggled(FilterFacet.Pinned, true))
            vm.onShowFilterSheet()
            advanceUntilIdle()

            assertEquals(
                FilterChipState(value = true, selected = true),
                vm.filterBottomSheetState.value.itemSection?.onlyPinned,
            )

            vm.onFilterAction(FilterAction.Toggled(FilterFacet.Pinned, true))
            advanceUntilIdle()

            assertEquals(
                FilterChipState(value = true, selected = false),
                vm.filterBottomSheetState.value.itemSection?.onlyPinned,
            )

            vm.onDismissFilterSheet()
            advanceUntilIdle()

            assertFalse(vm.filterBottomSheetState.value.isVisible)
            assertNull(vm.filterBottomSheetState.value.itemSection?.onlyPinned)
        }

    @Test
    fun `clearing filters outside the sheet drops the controls nothing matches right away`() =
        runTest(dispatcher) {
            loginRepository.seed(login("Loose"))

            val vm = viewModel()
            backgroundScope.launchCollect(vm)
            vm.onFilterAction(FilterAction.Toggled(FilterFacet.Pinned, true))
            vm.onFilterAction(FilterAction.ClearFilters)
            advanceUntilIdle()

            assertTrue(vm.filterBottomSheetState.value.isDefault)
            assertNull(vm.filterBottomSheetState.value.itemSection?.onlyPinned)
            assertEquals(1, vm.listItemState.value.items.size)
        }

    @Test
    fun `selecting a tag narrows the list to items carrying it`() = runTest(dispatcher) {
        val bank = Tag.of("Bank")!!
        val tagged = login("Tagged", tags = setOf(bank))
        val untagged = login("Untagged")
        loginRepository.seed(tagged, untagged)

        val vm = viewModel()
        backgroundScope.launchCollect(vm)
        advanceUntilIdle()

        vm.onFilterAction(FilterAction.Toggled(FilterFacet.Tags, bank))
        advanceUntilIdle()

        assertEquals(setOf(tagged.id), vm.listItemState.value.items.mapTo(mutableSetOf()) { it.id })
    }

    @Test
    fun `an expired card carries its status into the list state`() = runTest(dispatcher) {
        val card = expiredCard()
        creditCardRepository.seed(card)

        val vm = viewModel()
        backgroundScope.launchCollect(vm)
        advanceUntilIdle()

        assertEquals(
            mapOf(card.id to CardExpiryStatus.Expired),
            vm.listItemState.value.cardExpiryStatuses,
        )
    }

    private fun expiredCard() = CreditCard(
        id = newItemId(),
        vaultId = vaultId,
        name = "Old card",
        keyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
        timestamp = Timestamp(),
        tags = emptySet(),
        note = null,
        pinned = false,
        holder = null,
        cardNumber = null,
        cvv = null,
        expirationDate = YearMonth.now().minusYears(1),
    )
}

/**
 * `listItemState` is shared with `WhileSubscribed`, so a test that reads items back out of it
 * needs a live subscriber first, otherwise it only ever sees the initial empty state.
 */
private fun CoroutineScope.launchCollect(vm: ItemListViewModel) {
    launch { vm.listItemState.collect { } }
    launch { vm.filterBottomSheetState.collect { } }
}
