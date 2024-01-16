package de.davis.passwordmanager.ui.dashboard

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.davis.passwordmanager.R
import de.davis.passwordmanager.database.ElementType
import de.davis.passwordmanager.database.SecureElementManager
import de.davis.passwordmanager.database.dtos.Item
import de.davis.passwordmanager.database.dtos.SecureElement
import de.davis.passwordmanager.database.dtos.TagWithCount
import de.davis.passwordmanager.database.entities.onlyCustoms
import de.davis.passwordmanager.database.entities.tryGetElementType
import de.davis.passwordmanager.filter.Filter
import de.davis.passwordmanager.filter.applyFilter
import de.davis.passwordmanager.utils.PreferenceUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

private fun Context.getDefaultListState(): ListState {
    return if (PreferenceUtil.getBoolean(this, R.string.preference_feature_tag_layout, false))
        ListState.Tag
    else
        ListState.AllElements
}

class DashboardViewModel(private val application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<Pair<ListState, ListData<out Item>>?>(null)
    val state = _state.asStateFlow()

    private val _listState = MutableStateFlow(application.getDefaultListState())

    private val elementsFlow = SecureElementManager.getSecureElementsFlow()
    private val tagsFlow = SecureElementManager.getTagsWithCount()


    // For searching
    private val _searchResults =
        MutableStateFlow<Pair<String, List<SecureElement>>>("" to emptyList())
    val searchResults: StateFlow<Pair<String, List<SecureElement>>> = _searchResults.asStateFlow()
    private var searchJob: Job? = null

    private var ignoreElementTypes: List<ElementType> = emptyList()

    fun updateState(state: ListState) {
        _listState.value = state
    }

    fun initiateState(ignoreElementTypes: List<ElementType>) {
        this.ignoreElementTypes = ignoreElementTypes
        updateState(application.getDefaultListState())
    }

    fun search(query: String) {
        searchJob?.cancel()

        searchJob = viewModelScope.launch {
            _searchResults.value = query to SecureElementManager.getByTitle(query)
                .filter { !ignoreElementTypes.contains(it.detail.elementType) }
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
                    is ListState.Tag -> ListData.Tags(tagsWithCount.filter {
                        !ignoreElementTypes.contains(it.tag.tryGetElementType())
                    })

                    is ListState.Element -> {
                        val elements = secureElements
                            .filter { it.tags.any { tag -> listState.tagName == tag.name } }
                            .filter { !ignoreElementTypes.contains(it.detail.elementType) }

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