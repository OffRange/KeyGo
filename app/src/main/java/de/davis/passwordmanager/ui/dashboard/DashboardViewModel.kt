package de.davis.passwordmanager.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.passwordmanager.dashboard.Item
import de.davis.passwordmanager.database.SecureElementManager
import de.davis.passwordmanager.database.dtos.SecureElement
import de.davis.passwordmanager.database.dtos.TagWithCount
import de.davis.passwordmanager.database.entities.onlyCustoms
import de.davis.passwordmanager.filter.Filter
import de.davis.passwordmanager.filter.applyFilter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch


val DEFAULT_LIST_SATE = ListState.Tag

class DashboardViewModel : ViewModel() {

    private val _state = MutableStateFlow<Pair<ListState, ListData<out Item>>?>(null)
    val state = _state.asStateFlow()

    private val _listState = MutableStateFlow<ListState>(DEFAULT_LIST_SATE)

    private val elementsFlow = SecureElementManager.getSecureElementsFlow()
    private val tagsFlow = SecureElementManager.getTagsWithCount()


    // For searching
    private val _searchResults =
        MutableStateFlow<Pair<String, List<SecureElement>>>("" to emptyList())
    val searchResults: StateFlow<Pair<String, List<SecureElement>>> = _searchResults.asStateFlow()
    private var searchJob: Job? = null

    fun updateState(state: ListState) {
        _listState.value = state
    }

    fun search(query: String) {
        searchJob?.cancel()

        searchJob = viewModelScope.launch {
            _searchResults.value = query to SecureElementManager.getByTitle(query)
        }
    }

    init {
        viewModelScope.launch {
            combine(
                _listState,
                elementsFlow,
                tagsFlow,
                Filter.filterFlow
            ) { listState: ListState, secureElements: List<SecureElement>, tagsWithCount: List<TagWithCount>, filter ->
                listState to when (listState) {
                    is ListState.Tag -> ListData.Tags(tagsWithCount)

                    is ListState.Element -> {
                        val elements = secureElements
                            .filter { it.tags.any { tag -> listState.tagName == tag.name } }

                        ListData.Elements(
                            elements.applyFilter(filter),
                            elements.flatMap { it.tags.onlyCustoms() }.map { it.name }.distinct()
                        )
                    }

                    is ListState.AllElements -> ListData.Elements(secureElements, emptyList())
                }
            }.distinctUntilChangedBy { it.second.data }.collect {
                _state.value = it
            }
        }
    }
}

sealed class ListState {
    data object Tag : ListState()
    data class Element(val tagName: String) : ListState()
    data object AllElements : ListState()
}

sealed class ListData<E : Item>(val data: List<E>) {

    class Tags(data: List<TagWithCount>) : ListData<TagWithCount>(data)
    class Elements(data: List<SecureElement>, val tags: List<String>) :
        ListData<SecureElement>(data)
}